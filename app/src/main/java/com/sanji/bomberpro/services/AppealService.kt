package com.sanji.bomberpro.services

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sanji.bomberpro.FirebaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class Appeal(
    val id: String = "",
    val uid: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val banReason: String = "",
    val text: String = "",
    val status: String = "open",
    val adminReply: String = "",
    val createdAt: Long = 0L,
    val resolvedAt: Long = 0L,
    val resolvedBy: String = "",
)

class AppealService {

    private val db = FirebaseFirestore.getInstance()

    // ============================================================
    //  SUBMIT
    // ============================================================
    suspend fun submitAppeal(
        uid: String,
        userName: String,
        userEmail: String,
        banReason: String,
        text: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (text.isBlank()) return@withContext Result.failure(Exception("Appeal text required"))

            // check existing open appeal
            val existing = db.collection(FirebaseConfig.COL_APPEALS)
                .whereEqualTo("uid", uid)
                .whereEqualTo("status", "open")
                .limit(1)
                .get().await()
            if (!existing.isEmpty) {
                return@withContext Result.failure(Exception("You already have an open appeal"))
            }

            val ref = db.collection(FirebaseConfig.COL_APPEALS).document()
            ref.set(
                mapOf(
                    "id" to ref.id,
                    "uid" to uid,
                    "userName" to userName.take(64),
                    "userEmail" to userEmail.take(80),
                    "banReason" to banReason.take(200),
                    "text" to text.trim().take(1000),
                    "status" to "open",
                    "adminReply" to "",
                    "createdAt" to System.currentTimeMillis() / 1000L,
                    "resolvedAt" to 0L,
                    "resolvedBy" to "",
                )
            ).await()

            Result.success(ref.id)
        } catch (e: Exception) {
            Log.e(TAG, "submitAppeal failed", e)
            Result.failure(e)
        }
    }

    // ============================================================
    //  LIST — user's own appeals
    // ============================================================
    suspend fun listUserAppeals(uid: String, limit: Long = 20): List<Appeal> =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_APPEALS)
                    .whereEqualTo("uid", uid)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(limit)
                    .get().await()
                    .documents.mapNotNull { parse(it.id, it.data ?: emptyMap()) }
            } catch (e: Exception) {
                Log.w(TAG, "listUserAppeals failed", e)
                emptyList()
            }
        }

    // ============================================================
    //  LIST — open appeals (admin)
    // ============================================================
    suspend fun listOpenAppeals(limit: Long = 100): List<Appeal> = withContext(Dispatchers.IO) {
        try {
            db.collection(FirebaseConfig.COL_APPEALS)
                .whereEqualTo("status", "open")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get().await()
                .documents.mapNotNull { parse(it.id, it.data ?: emptyMap()) }
        } catch (e: Exception) {
            Log.w(TAG, "listOpenAppeals failed", e)
            emptyList()
        }
    }

    // ============================================================
    //  GET one
    // ============================================================
    suspend fun getAppeal(id: String): Appeal? = withContext(Dispatchers.IO) {
        try {
            val snap = db.collection(FirebaseConfig.COL_APPEALS).document(id).get().await()
            if (!snap.exists()) return@withContext null
            parse(snap.id, snap.data ?: emptyMap())
        } catch (e: Exception) {
            null
        }
    }

    // ============================================================
    //  RESOLVE — accept appeal (admin unban)
    // ============================================================
    suspend fun resolveAppeal(
        appealId: String,
        adminUid: String,
        adminReply: String = "",
        unbanUser: Boolean = true,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val appealRef = db.collection(FirebaseConfig.COL_APPEALS).document(appealId)
            val snap = appealRef.get().await()
            if (!snap.exists()) return@withContext false

            val targetUid = snap.getString("uid") ?: ""

            db.runBatch { batch ->
                batch.update(
                    appealRef,
                    mapOf(
                        "status" to "resolved",
                        "adminReply" to adminReply.take(500),
                        "resolvedAt" to System.currentTimeMillis() / 1000L,
                        "resolvedBy" to adminUid,
                    )
                )
                if (unbanUser && targetUid.isNotBlank()) {
                    val userRef = db.collection(FirebaseConfig.COL_USERS).document(targetUid)
                    batch.update(
                        userRef,
                        "banned", false,
                        "banReason", "",
                    )
                }
            }.await()

            // audit
            try {
                db.collection(FirebaseConfig.COL_AUDIT_LOG).add(
                    mapOf(
                        "adminUid" to adminUid,
                        "action" to "appeal_resolved",
                        "targetUid" to targetUid,
                        "amount" to 0L,
                        "reason" to "appeal=$appealId unban=$unbanUser",
                        "ts" to System.currentTimeMillis() / 1000L,
                    )
                ).await()
            } catch (_: Exception) {}

            true
        } catch (e: Exception) {
            Log.w(TAG, "resolveAppeal failed", e)
            false
        }
    }

    // ============================================================
    //  REJECT — keep ban
    // ============================================================
    suspend fun rejectAppeal(
        appealId: String,
        adminUid: String,
        adminReply: String = "",
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            db.collection(FirebaseConfig.COL_APPEALS).document(appealId)
                .update(
                    mapOf(
                        "status" to "rejected",
                        "adminReply" to adminReply.take(500),
                        "resolvedAt" to System.currentTimeMillis() / 1000L,
                        "resolvedBy" to adminUid,
                    )
                ).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "rejectAppeal failed", e)
            false
        }
    }

    // ============================================================
    //  CLOSE — mark stale
    // ============================================================
    suspend fun closeAppeal(appealId: String, adminUid: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_APPEALS).document(appealId)
                    .update(
                        mapOf(
                            "status" to "closed",
                            "resolvedAt" to System.currentTimeMillis() / 1000L,
                            "resolvedBy" to adminUid,
                        )
                    ).await()
                true
            } catch (e: Exception) {
                false
            }
        }

    // ============================================================
    //  PARSE
    // ============================================================
    private fun parse(id: String, map: Map<String, Any?>): Appeal? {
        if (map.isEmpty()) return null
        return Appeal(
            id = id,
            uid = map["uid"] as? String ?: "",
            userName = map["userName"] as? String ?: "",
            userEmail = map["userEmail"] as? String ?: "",
            banReason = map["banReason"] as? String ?: "",
            text = map["text"] as? String ?: "",
            status = map["status"] as? String ?: "open",
            adminReply = map["adminReply"] as? String ?: "",
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
            resolvedAt = (map["resolvedAt"] as? Number)?.toLong() ?: 0L,
            resolvedBy = map["resolvedBy"] as? String ?: "",
        )
    }

    companion object {
        private const val TAG = "AppealService"
    }
}