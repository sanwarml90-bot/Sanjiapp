package com.sanji.bomberpro.services

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sanji.bomberpro.FirebaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class UserNotification(
    val id: String = "",
    val uid: String = "",
    val title: String = "",
    val body: String = "",
    val type: String = "",
    val read: Boolean = false,
    val ts: Long = 0L,
    val priority: String = "normal",
    val broadcastId: String = "",
)

class NotificationsService {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    private fun notifCol() = db.collection(FirebaseConfig.COL_NOTIFICATIONS)

    // ============================================================
    //  LIST
    // ============================================================
    /**
     * Fetch user's notifications.
     *
     * NOTE: Uses single `whereEqualTo("uid")` query + client-side sort.
     * Avoids composite index requirement (`uid + ts` or `uid + read`)
     * which fails silently in production without proper indexes.
     */
    suspend fun listNotifications(uid: String, limit: Long = 50): List<UserNotification> =
        withContext(Dispatchers.IO) {
            try {
                val snap = notifCol()
                    .whereEqualTo("uid", uid)
                    .limit(limit.coerceAtLeast(100))
                    .get().await()

                snap.documents.mapNotNull { d ->
                    val map = d.data ?: return@mapNotNull null
                    UserNotification(
                        id = d.id,
                        uid = map["uid"] as? String ?: "",
                        title = map["title"] as? String ?: "",
                        body = map["body"] as? String ?: "",
                        type = map["type"] as? String ?: "",
                        read = map["read"] as? Boolean ?: false,
                        ts = (map["ts"] as? Number)?.toLong() ?: 0L,
                        priority = map["priority"] as? String ?: "normal",
                        broadcastId = map["broadcastId"] as? String ?: "",
                    )
                }
                    .sortedByDescending { it.ts }
                    .take(limit.toInt())
            } catch (e: Exception) {
                emptyList()
            }
        }

    // ============================================================
    //  MARK
    // ============================================================
    suspend fun markRead(notificationId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            notifCol().document(notificationId).update("read", true).await()
            true
        } catch (_: Exception) { false }
    }

    suspend fun markAllRead(uid: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // single equality — safe, no index needed
            val unread = notifCol()
                .whereEqualTo("uid", uid)
                .limit(500)
                .get().await()
                .documents
                .filter { it.getBoolean("read") != true }

            if (unread.isEmpty()) return@withContext true
            val chunks = unread.chunked(400)
            for (chunk in chunks) {
                val batch = db.batch()
                for (d in chunk) batch.update(d.reference, "read", true)
                batch.commit().await()
            }
            true
        } catch (_: Exception) { false }
    }

    // ============================================================
    //  UNREAD COUNT — live flow
    // ============================================================
    /**
     * Live unread count for the notification bell badge.
     * Single equality listener + client-side count (no composite index needed).
     */
    fun unreadCountFlow(uid: String): Flow<Int> = callbackFlow {
        val listener = notifCol()
            .whereEqualTo("uid", uid)
            .limit(500)
            .addSnapshotListener { snap, _ ->
                if (snap == null) {
                    trySend(0)
                    return@addSnapshotListener
                }
                val unread = snap.documents.count { it.getBoolean("read") != true }
                trySend(unread)
            }
        awaitClose { listener.remove() }
    }

    // ============================================================
    //  DELETE (admin)
    // ============================================================
    suspend fun deleteNotification(notificationId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                notifCol().document(notificationId).delete().await()
                true
            } catch (_: Exception) { false }
        }

    suspend fun deleteAllForUser(uid: String): Int = withContext(Dispatchers.IO) {
        try {
            val docs = notifCol()
                .whereEqualTo("uid", uid)
                .limit(500)
                .get().await()
                .documents
            val chunks = docs.chunked(400)
            var deleted = 0
            for (chunk in chunks) {
                val batch = db.batch()
                for (d in chunk) batch.delete(d.reference)
                batch.commit().await()
                deleted += chunk.size
            }
            deleted
        } catch (_: Exception) { 0 }
    }

    // ============================================================
    //  WRITE (used by other services as a helper)
    // ============================================================
    suspend fun pushNotification(
        uid: String,
        title: String,
        body: String,
        type: String = "default",
        priority: String = "normal",
        broadcastId: String = "",
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val ref = notifCol().document()
            ref.set(
                mapOf(
                    "id" to ref.id,
                    "uid" to uid,
                    "title" to title.take(120),
                    "body" to body.take(2000),
                    "type" to type,
                    "priority" to priority,
                    "broadcastId" to broadcastId,
                    "read" to false,
                    "ts" to System.currentTimeMillis() / 1000L,
                )
            ).await()
            true
        } catch (_: Exception) { false }
    }
}