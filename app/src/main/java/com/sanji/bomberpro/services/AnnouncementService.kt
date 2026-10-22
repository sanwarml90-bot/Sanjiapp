package com.sanji.bomberpro.services

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sanji.bomberpro.FirebaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class Announcement(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val sentBy: String = "",
    val sentByName: String = "",
    val priority: String = "normal",
    val recipients: Int = 0,
    val createdAt: Long = 0L,
)

data class BroadcastPushResult(
    val totalUsers: Int = 0,
    val notificationsWritten: Int = 0,
    val error: String? = null,
)

/**
 * Announcements are admin-sent broadcast messages stored in
 * `announcements/{id}` collection. Each announcement is also pushed
 * to every user as an item in `notifications` (used by NotificationsScreen).
 *
 * This service is the *admin-side* announcer. It writes both the
 * announcement doc AND the per-user notification docs.
 */
class AnnouncementService {

    private val db = FirebaseFirestore.getInstance()

    // ============================================================
    //  CREATE + PUSH
    // ============================================================
    suspend fun createAndBroadcast(
        title: String,
        body: String,
        sentBy: String,
        sentByName: String,
        priority: String = "normal",
    ): Result<BroadcastPushResult> = withContext(Dispatchers.IO) {
        try {
            val cleanTitle = title.trim().take(120)
            val cleanBody = body.trim().take(2000)
            if (cleanTitle.isBlank()) return@withContext Result.failure(Exception("Title required"))
            if (cleanBody.isBlank()) return@withContext Result.failure(Exception("Body required"))

            val now = System.currentTimeMillis() / 1000L

            // get all users
            val users = db.collection(FirebaseConfig.COL_USERS).get().await().documents
            val total = users.size

            // 1. write announcement doc
            val annRef = db.collection(FirebaseConfig.COL_ANNOUNCEMENTS).document()
            annRef.set(
                mapOf(
                    "id" to annRef.id,
                    "title" to cleanTitle,
                    "body" to cleanBody,
                    "sentBy" to sentBy,
                    "sentByName" to sentByName.take(64),
                    "priority" to priority,
                    "recipients" to total.toLong(),
                    "createdAt" to now,
                )
            ).await()

            // 2. write per-user notifications (batched 400)
            var written = 0
            val chunks = users.chunked(400)
            for (chunk in chunks) {
                val batch = db.batch()
                for (u in chunk) {
                    val nRef = db.collection(FirebaseConfig.COL_NOTIFICATIONS).document()
                    batch.set(
                        nRef,
                        mapOf(
                            "id" to nRef.id,
                            "uid" to u.id,
                            "title" to cleanTitle,
                            "body" to cleanBody,
                            "type" to "announcement",
                            "priority" to priority,
                            "broadcastId" to annRef.id,
                            "read" to false,
                            "ts" to now,
                        )
                    )
                    written++
                }
                batch.commit().await()
            }

            // 3. audit
            try {
                db.collection(FirebaseConfig.COL_AUDIT_LOG).add(
                    mapOf(
                        "adminUid" to sentBy,
                        "action" to "announcement_broadcast",
                        "targetUid" to "",
                        "amount" to 0L,
                        "reason" to "id=${annRef.id} recipients=$written",
                        "ts" to now,
                    )
                ).await()
            } catch (_: Exception) {}

            Result.success(
                BroadcastPushResult(
                    totalUsers = total,
                    notificationsWritten = written,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "createAndBroadcast failed", e)
            Result.failure(e)
        }
    }

    // ============================================================
    //  LIST — announcements for admin
    // ============================================================
    suspend fun listAnnouncements(limit: Long = 50): List<Announcement> =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_ANNOUNCEMENTS)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(limit)
                    .get().await()
                    .documents.mapNotNull { parse(it.id, it.data ?: emptyMap()) }
            } catch (e: Exception) {
                Log.w(TAG, "listAnnouncements failed", e)
                emptyList()
            }
        }

    // ============================================================
    //  DELETE
    // ============================================================
    suspend fun deleteAnnouncement(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            db.collection(FirebaseConfig.COL_ANNOUNCEMENTS).document(id).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ============================================================
    //  READ announcements (user view — from notifications)
    // ============================================================
    suspend fun listUserAnnouncements(uid: String, limit: Long = 50): List<Announcement> =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_NOTIFICATIONS)
                    .whereEqualTo("uid", uid)
                    .whereEqualTo("type", "announcement")
                    .orderBy("ts", Query.Direction.DESCENDING)
                    .limit(limit)
                    .get().await()
                    .documents.mapNotNull { d ->
                        val map = d.data ?: return@mapNotNull null
                        Announcement(
                            id = map["broadcastId"] as? String ?: d.id,
                            title = map["title"] as? String ?: "",
                            body = map["body"] as? String ?: "",
                            priority = map["priority"] as? String ?: "normal",
                            createdAt = (map["ts"] as? Number)?.toLong() ?: 0L,
                        )
                    }
            } catch (e: Exception) {
                Log.w(TAG, "listUserAnnouncements failed", e)
                emptyList()
            }
        }

    // ============================================================
    //  PARSE
    // ============================================================
    private fun parse(id: String, map: Map<String, Any?>): Announcement? {
        if (map.isEmpty()) return null
        return Announcement(
            id = id,
            title = map["title"] as? String ?: "",
            body = map["body"] as? String ?: "",
            sentBy = map["sentBy"] as? String ?: "",
            sentByName = map["sentByName"] as? String ?: "",
            priority = map["priority"] as? String ?: "normal",
            recipients = (map["recipients"] as? Number)?.toInt() ?: 0,
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
        )
    }

    companion object {
        private const val TAG = "AnnouncementService"
    }
}