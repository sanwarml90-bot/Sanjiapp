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
)

class NotificationsService {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    private fun notifCol() = db.collection("notifications")

    suspend fun listNotifications(uid: String, limit: Long = 50): List<UserNotification> =
        withContext(Dispatchers.IO) {
            try {
                notifCol()
                    .whereEqualTo("uid", uid)
                    .orderBy("ts", Query.Direction.DESCENDING)
                    .limit(limit).get().await()
                    .documents.mapNotNull { d ->
                        val map = d.data ?: return@mapNotNull null
                        UserNotification(
                            id = d.id,
                            uid = map["uid"] as? String ?: "",
                            title = map["title"] as? String ?: "",
                            body = map["body"] as? String ?: "",
                            type = map["type"] as? String ?: "",
                            read = map["read"] as? Boolean ?: false,
                            ts = (map["ts"] as? Number)?.toLong() ?: 0L,
                        )
                    }
            } catch (_: Exception) { emptyList() }
        }

    suspend fun markRead(notificationId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            notifCol().document(notificationId).update("read", true).await()
            true
        } catch (_: Exception) { false }
    }

    suspend fun markAllRead(uid: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val unread = notifCol()
                .whereEqualTo("uid", uid)
                .whereEqualTo("read", false)
                .get().await().documents
            if (unread.isEmpty()) return@withContext true
            val chunks = unread.chunked(500)
            for (chunk in chunks) {
                val batch = db.batch()
                for (d in chunk) batch.update(d.reference, "read", true)
                batch.commit().await()
            }
            true
        } catch (_: Exception) { false }
    }

    fun unreadCountFlow(uid: String): Flow<Int> = callbackFlow {
        val listener = notifCol()
            .whereEqualTo("uid", uid)
            .whereEqualTo("read", false)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.size() ?: 0)
            }
        awaitClose { listener.remove() }
    }
}