package com.sanji.bomberpro.services

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class TicketReply(
    val fromUid: String = "",
    val text: String = "",
    val ts: Long = 0L,
)

data class Ticket(
    val id: String = "",
    val uid: String = "",
    val userName: String = "",
    val text: String = "",
    val status: String = "open",
    val ts: Long = 0L,
    val lastUpdate: Long = 0L,
    val replies: List<TicketReply> = emptyList(),
)

/**
 * Legacy support-appeal model.
 * NOTE: distinct from `AppealRecord` in AppealService.
 * This one is used by the Support ticket/ban-appeal flow.
 * Writes to `support_appeals` collection (NOT `appeals`).
 * The newer `appeals` collection is reserved for AppealService.
 */
data class Appeal(
    val id: String = "",
    val uid: String = "",
    val userName: String = "",
    val text: String = "",
    val status: String = "open",
    val ts: Long = 0L,
)

class SupportService {

    private val db = FirebaseFirestore.getInstance()
    private val ticketsCol get() = db.collection("tickets")
    // Separate collection — avoids schema collision with AppealService's `appeals`.
    private val appealsCol get() = db.collection("support_appeals")

    // ============================================================
    //  TICKETS
    // ============================================================
    suspend fun submitTicket(uid: String, userName: String, text: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val cleanText = text.trim().take(1000)
                if (cleanText.isBlank()) {
                    return@withContext Result.failure(Exception("Message empty"))
                }
                val now = System.currentTimeMillis() / 1000L
                val ref = ticketsCol.document()
                ref.set(
                    mapOf(
                        "id" to ref.id,
                        "uid" to uid,
                        "userName" to userName.take(64),
                        "text" to cleanText,
                        "status" to "open",
                        "ts" to now,
                        "lastUpdate" to now,
                        "replies" to emptyList<Map<String, Any>>(),
                    )
                ).await()
                Result.success(ref.id)
            } catch (e: Exception) { Result.failure(e) }
        }

    suspend fun listMyTickets(uid: String, limit: Long = 50): List<Ticket> =
        withContext(Dispatchers.IO) {
            try {
                ticketsCol
                    .whereEqualTo("uid", uid)
                    .orderBy("ts", Query.Direction.DESCENDING)
                    .limit(limit).get().await()
                    .documents.mapNotNull { parseTicket(it.id, it.data ?: emptyMap()) }
            } catch (_: Exception) { emptyList() }
        }

    suspend fun listOpenTickets(limit: Long = 100): List<Ticket> =
        withContext(Dispatchers.IO) {
            try {
                ticketsCol
                    .whereEqualTo("status", "open")
                    .orderBy("lastUpdate", Query.Direction.DESCENDING)
                    .limit(limit).get().await()
                    .documents.mapNotNull { parseTicket(it.id, it.data ?: emptyMap()) }
            } catch (_: Exception) { emptyList() }
        }

    suspend fun findTicket(id: String): Ticket? = withContext(Dispatchers.IO) {
        try {
            val snap = ticketsCol.document(id).get().await()
            if (!snap.exists()) return@withContext null
            parseTicket(snap.id, snap.data ?: emptyMap())
        } catch (_: Exception) { null }
    }

    suspend fun addTicketReply(ticketId: String, fromUid: String, text: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val clean = text.trim().take(1000)
                if (clean.isBlank()) return@withContext false
                val now = System.currentTimeMillis() / 1000L
                val reply = mapOf(
                    "fromUid" to fromUid,
                    "text" to clean,
                    "ts" to now,
                )
                ticketsCol.document(ticketId).update(
                    "replies", com.google.firebase.firestore.FieldValue.arrayUnion(reply),
                    "lastUpdate", now,
                ).await()
                true
            } catch (_: Exception) { false }
        }

    suspend fun closeTicket(ticketId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            ticketsCol.document(ticketId)
                .update("status", "closed").await()
            true
        } catch (_: Exception) { false }
    }

    private fun parseTicket(id: String, map: Map<String, Any?>): Ticket? {
        if (map.isEmpty()) return null
        val repliesRaw = map["replies"] as? List<*> ?: emptyList<Any>()
        val replies = repliesRaw.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            TicketReply(
                fromUid = (m["fromUid"] ?: "").toString(),
                text = m["text"] as? String ?: "",
                ts = (m["ts"] as? Number)?.toLong() ?: 0L,
            )
        }
        return Ticket(
            id = id,
            uid = (map["uid"] ?: "").toString(),
            userName = map["userName"] as? String ?: "",
            text = map["text"] as? String ?: "",
            status = map["status"] as? String ?: "open",
            ts = (map["ts"] as? Number)?.toLong() ?: 0L,
            lastUpdate = (map["lastUpdate"] as? Number)?.toLong() ?: 0L,
            replies = replies,
        )
    }

    // ============================================================
    //  APPEALS (support-style, distinct collection)
    // ============================================================
    suspend fun submitAppeal(uid: String, userName: String, text: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val cleanText = text.trim().take(1000)
                if (cleanText.isBlank()) {
                    return@withContext Result.failure(Exception("Message empty"))
                }
                // Check if already has open appeal
                val existing = appealsCol
                    .whereEqualTo("uid", uid)
                    .whereEqualTo("status", "open")
                    .limit(1).get().await()
                if (!existing.isEmpty) {
                    return@withContext Result.failure(Exception("You already have an open appeal"))
                }
                val now = System.currentTimeMillis() / 1000L
                val ref = appealsCol.document()
                ref.set(
                    mapOf(
                        "id" to ref.id,
                        "uid" to uid,
                        "userName" to userName.take(64),
                        "text" to cleanText,
                        "status" to "open",
                        "ts" to now,
                    )
                ).await()
                Result.success(ref.id)
            } catch (e: Exception) { Result.failure(e) }
        }

    suspend fun listAppeals(onlyOpen: Boolean = true, limit: Long = 100): List<Appeal> =
        withContext(Dispatchers.IO) {
            try {
                var q: Query = appealsCol
                if (onlyOpen) q = q.whereEqualTo("status", "open")
                q.orderBy("ts", Query.Direction.DESCENDING)
                    .limit(limit).get().await()
                    .documents.mapNotNull { d ->
                        val map = d.data ?: return@mapNotNull null
                        Appeal(
                            id = d.id,
                            uid = (map["uid"] ?: "").toString(),
                            userName = map["userName"] as? String ?: "",
                            text = map["text"] as? String ?: "",
                            status = map["status"] as? String ?: "open",
                            ts = (map["ts"] as? Number)?.toLong() ?: 0L,
                        )
                    }
            } catch (_: Exception) { emptyList() }
        }

    suspend fun resolveAppeal(appealId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            appealsCol.document(appealId)
                .update("status", "resolved").await()
            true
        } catch (_: Exception) { false }
    }
}