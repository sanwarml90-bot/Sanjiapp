package com.sanji.bomberpro.services

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.sanji.bomberpro.FirebaseConfig
import com.sanji.bomberpro.data.PaymentRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Payment submission — in-app only.
 *
 * Flow:
 *  1. User taps buy (packs/plans) → PaymentSubmitScreen opens
 *  2. User pays via UPI deeplink / QR / manually
 *  3. User enters UTR (UPI transaction reference) + optional note → submits
 *  4. Request saved to `payment_requests` with status=pending
 *  5. Owner reviews in Admin Panel → Payment Review → Approve/Reject
 *  6. On approve, credits/plan granted automatically
 *
 * No bot. No Telegram. No screenshot. Only UTR + amount.
 */
class PaymentService(private val context: Context) {

    private val db = FirebaseFirestore.getInstance()

    // ============================================================
    //  SUBMIT
    // ============================================================
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
    ): Result<PaymentRequest> = withContext(Dispatchers.IO) {
        try {
            val cleanUtr = utr.trim()
            if (cleanUtr.length < 6) {
                return@withContext Result.failure(Exception("UTR must be at least 6 characters"))
            }

            val reqRef = db.collection(FirebaseConfig.COL_PAYMENT_REQUESTS).document()
            val requestId = reqRef.id
            val now = System.currentTimeMillis() / 1000L

            val doc = hashMapOf<String, Any>(
                "id" to requestId,
                "uid" to uid,
                "userName" to userName.take(64),
                "userEmail" to userEmail.take(80),
                "type" to type,
                "packId" to packId,
                "packName" to packName.take(60),
                "creditsAmount" to creditsAmount.toLong(),
                "plan" to plan,
                "amount" to amountInr.toLong(),
                "utr" to cleanUtr.take(40),
                "description" to description.take(300),
                "status" to "pending",
                "telegramMsgId" to 0L,
                "createdAt" to now,
                "reviewedAt" to 0L,
                "reviewedBy" to "",
                "rejectionReason" to "",
                "source" to "in-app",
            )
            reqRef.set(doc).await()

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
                    utr = cleanUtr,
                    description = description,
                    status = "pending",
                    telegramMsgId = 0L,
                    createdAt = now,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============================================================
    //  OBSERVE (user side — live status)
    // ============================================================
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

    // ============================================================
    //  PENDING (user side)
    // ============================================================
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

    // ============================================================
    //  PARSE
    // ============================================================
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