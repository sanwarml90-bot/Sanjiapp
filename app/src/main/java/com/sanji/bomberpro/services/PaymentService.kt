package com.sanji.bomberpro.services

import android.content.Context
import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.sanji.bomberpro.FirebaseConfig
import com.sanji.bomberpro.data.PaymentRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class PaymentService(private val context: Context) {

    private val db = FirebaseFirestore.getInstance()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun submitPayment(
        uid: String,
        userName: String,
        userEmail: String,
        type: String,
        packId: String,
        packName: String,
        creditsAmount: Int,
        plan: String,
        amountInr: Int,
        utr: String,
        description: String,
        screenshotUri: Uri,
    ): Result<PaymentRequest> = withContext(Dispatchers.IO) {
        try {
            val reqRef = db.collection(FirebaseConfig.COL_PAYMENT_REQUESTS).document()
            val requestId = reqRef.id
            val now = System.currentTimeMillis() / 1000L

            val initial = hashMapOf<String, Any>(
                "id" to requestId,
                "uid" to uid,
                "userName" to userName,
                "userEmail" to userEmail,
                "type" to type,
                "packId" to packId,
                "packName" to packName,
                "creditsAmount" to creditsAmount.toLong(),
                "plan" to plan,
                "amount" to amountInr.toLong(),
                "utr" to utr.take(40),
                "description" to description.take(300),
                "status" to "pending",
                "telegramMsgId" to 0L,
                "createdAt" to now,
                "reviewedAt" to 0L,
                "reviewedBy" to "",
                "rejectionReason" to "",
            )
            reqRef.set(initial).await()

            val fileBytes = context.contentResolver.openInputStream(screenshotUri)?.use { it.readBytes() }
                ?: throw Exception("Could not read screenshot")

            val caption = buildCaption(
                userName = userName, userEmail = userEmail, uid = uid,
                type = type, packName = packName, creditsAmount = creditsAmount,
                plan = plan, amountInr = amountInr, utr = utr,
                description = description, requestId = requestId,
            )

            val keyboardJson = JSONObject().apply {
                put("inline_keyboard", JSONArray().apply {
                    put(JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "✅ Approve")
                            put("callback_data", "approve:$requestId")
                        })
                        put(JSONObject().apply {
                            put("text", "❌ Reject")
                            put("callback_data", "reject:$requestId")
                        })
                    })
                })
            }.toString()

            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", FirebaseConfig.PAYMENT_BOT_CHAT_ID)
                .addFormDataPart("caption", caption)
                .addFormDataPart("parse_mode", "HTML")
                .addFormDataPart("reply_markup", keyboardJson)
                .addFormDataPart(
                    "photo",
                    "screenshot.jpg",
                    fileBytes.toRequestBody("image/jpeg".toMediaType()),
                )
                .build()

            val url = "https://api.telegram.org/bot${FirebaseConfig.PAYMENT_BOT_TOKEN}/sendPhoto"
            val request = Request.Builder().url(url).post(multipart).build()

            val (success, msgId) = try {
                http.newCall(request).execute().use { resp ->
                    val bodyStr = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        val json = JSONObject(bodyStr)
                        if (json.optBoolean("ok", false)) {
                            val result = json.optJSONObject("result")
                            val mid = result?.optLong("message_id", 0L) ?: 0L
                            true to mid
                        } else false to 0L
                    } else false to 0L
                }
            } catch (e: Exception) {
                false to 0L
            }

            if (!success) {
                reqRef.delete().await()
                return@withContext Result.failure(Exception("Telegram bot upload failed"))
            }

            reqRef.update("telegramMsgId", msgId).await()

            Result.success(
                PaymentRequest(
                    id = requestId,
                    uid = uid,
                    userName = userName,
                    userEmail = userEmail,
                    type = type,
                    packId = packId,
                    packName = packName,
                    creditsAmount = creditsAmount,
                    plan = plan,
                    amount = amountInr,
                    utr = utr,
                    description = description,
                    status = "pending",
                    telegramMsgId = msgId,
                    createdAt = now,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildCaption(
        userName: String, userEmail: String, uid: String,
        type: String, packName: String, creditsAmount: Int,
        plan: String, amountInr: Int, utr: String,
        description: String, requestId: String,
    ): String {
        val itemLine = if (type == "subscription") "📦 Plan: <b>$plan</b>" else "📦 Pack: <b>$packName</b> ($creditsAmount credits)"
        return """
🆕 <b>PAYMENT REQUEST</b>

👤 <b>${escapeHtml(userName)}</b>
📧 ${escapeHtml(userEmail)}
🆔 <code>$uid</code>

$itemLine
💵 Amount: <b>₹$amountInr</b>

🔢 UTR: <code>${escapeHtml(utr)}</code>
📝 Note: ${escapeHtml(description.ifBlank { "—" })}

🕐 ${nowIst()}

🎫 <code>$requestId</code>
        """.trimIndent()
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun nowIst(): String {
        val tz = java.util.TimeZone.getTimeZone("Asia/Kolkata")
        val fmt = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        fmt.timeZone = tz
        return fmt.format(java.util.Date())
    }

    fun observeRequest(requestId: String): Flow<PaymentRequest?> = callbackFlow {
        val ref = db.collection(FirebaseConfig.COL_PAYMENT_REQUESTS).document(requestId)
        val listener = ref.addSnapshotListener { snap, _ ->
            if (snap != null && snap.exists()) {
                trySend(parseRequest(snap.id, snap.data ?: emptyMap()))
            } else {
                trySend(null)
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun getPendingRequests(uid: String): List<PaymentRequest> = withContext(Dispatchers.IO) {
        try {
            db.collection(FirebaseConfig.COL_PAYMENT_REQUESTS)
                .whereEqualTo("uid", uid)
                .whereEqualTo("status", "pending")
                .get().await()
                .documents.map { parseRequest(it.id, it.data ?: emptyMap()) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseRequest(id: String, map: Map<String, Any?>): PaymentRequest {
        return PaymentRequest(
            id = id,
            uid = map["uid"] as? String ?: "",
            userName = map["userName"] as? String ?: "",
            userEmail = map["userEmail"] as? String ?: "",
            type = map["type"] as? String ?: "credits",
            packId = map["packId"] as? String ?: "",
            packName = map["packName"] as? String ?: "",
            creditsAmount = (map["creditsAmount"] as? Number)?.toInt() ?: 0,
            plan = map["plan"] as? String ?: "",
            amount = (map["amount"] as? Number)?.toInt() ?: 0,
            utr = map["utr"] as? String ?: "",
            description = map["description"] as? String ?: "",
            status = map["status"] as? String ?: "pending",
            telegramMsgId = (map["telegramMsgId"] as? Number)?.toLong() ?: 0L,
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
            reviewedAt = (map["reviewedAt"] as? Number)?.toLong() ?: 0L,
            reviewedBy = map["reviewedBy"] as? String ?: "",
            rejectionReason = map["rejectionReason"] as? String ?: "",
        )
    }
}