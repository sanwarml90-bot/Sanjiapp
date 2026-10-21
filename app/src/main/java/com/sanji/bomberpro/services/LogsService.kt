package com.sanji.bomberpro.services

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sanji.bomberpro.FirebaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class ActivityLogEntry(
    val id: String = "",
    val uid: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val action: String = "",
    val targetFull: String = "",
    val targetMasked: String = "",
    val details: Map<String, Any?> = emptyMap(),
    val ts: Long = 0L,
)

data class AuditLogEntry(
    val id: String = "",
    val adminUid: String = "",
    val action: String = "",
    val targetUid: String = "",
    val amount: Int = 0,
    val reason: String = "",
    val ts: Long = 0L,
)

data class PaymentLogEntry(
    val id: String = "",
    val uid: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val type: String = "",
    val packName: String = "",
    val plan: String = "",
    val creditsAmount: Int = 0,
    val amount: Int = 0,
    val utr: String = "",
    val status: String = "",
    val createdAt: Long = 0L,
    val reviewedAt: Long = 0L,
)

class LogsService {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun listActivityLogs(limit: Long = 200): List<ActivityLogEntry> =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_ACTIVITY_LOG)
                    .orderBy("ts", Query.Direction.DESCENDING)
                    .limit(limit).get().await()
                    .documents.mapNotNull { d ->
                        val map = d.data ?: return@mapNotNull null
                        val detailsRaw = map["details"] as? Map<*, *>
                        ActivityLogEntry(
                            id = d.id,
                            uid = map["uid"] as? String ?: "",
                            userName = map["userName"] as? String ?: "",
                            userEmail = map["userEmail"] as? String ?: "",
                            action = map["action"] as? String ?: "",
                            targetFull = map["targetFull"] as? String ?: "",
                            targetMasked = map["targetMasked"] as? String ?: "",
                            details = detailsRaw?.entries?.associate { it.key.toString() to it.value } ?: emptyMap(),
                            ts = (map["ts"] as? Number)?.toLong() ?: 0L,
                        )
                    }
            } catch (_: Exception) { emptyList() }
        }

    suspend fun listAuditLogs(limit: Long = 200): List<AuditLogEntry> =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_AUDIT_LOG)
                    .orderBy("ts", Query.Direction.DESCENDING)
                    .limit(limit).get().await()
                    .documents.mapNotNull { d ->
                        val map = d.data ?: return@mapNotNull null
                        AuditLogEntry(
                            id = d.id,
                            adminUid = (map["adminUid"] ?: "").toString(),
                            action = map["action"] as? String ?: "",
                            targetUid = (map["targetUid"] ?: "").toString(),
                            amount = (map["amount"] as? Number)?.toInt() ?: 0,
                            reason = map["reason"] as? String ?: "",
                            ts = (map["ts"] as? Number)?.toLong() ?: 0L,
                        )
                    }
            } catch (_: Exception) { emptyList() }
        }

    suspend fun listPaymentLogs(limit: Long = 200): List<PaymentLogEntry> =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_PAYMENT_REQUESTS)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(limit).get().await()
                    .documents.mapNotNull { d ->
                        val map = d.data ?: return@mapNotNull null
                        PaymentLogEntry(
                            id = d.id,
                            uid = (map["uid"] ?: "").toString(),
                            userName = map["userName"] as? String ?: "",
                            userEmail = map["userEmail"] as? String ?: "",
                            type = map["type"] as? String ?: "credits",
                            packName = map["packName"] as? String ?: "",
                            plan = map["plan"] as? String ?: "",
                            creditsAmount = (map["creditsAmount"] as? Number)?.toInt() ?: 0,
                            amount = (map["amount"] as? Number)?.toInt() ?: 0,
                            utr = map["utr"] as? String ?: "",
                            status = map["status"] as? String ?: "pending",
                            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                            reviewedAt = (map["reviewedAt"] as? Number)?.toLong() ?: 0L,
                        )
                    }
            } catch (_: Exception) { emptyList() }
        }
}