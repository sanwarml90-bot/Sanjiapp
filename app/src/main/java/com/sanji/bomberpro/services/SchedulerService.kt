package com.sanji.bomberpro.services

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sanji.bomberpro.FirebaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class ScheduledBlast(
    val id: String = "",
    val uid: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val target: String = "",
    val message: String = "",
    val count: Int = 0,
    val power: Int = 2,
    val speed: String = "FAST",
    val scheduledAt: Long = 0L,
    val createdAt: Long = 0L,
    val status: String = "pending",
    val firedAt: Long = 0L,
    val repeatMinutes: Int = 0,
    val lastError: String = "",
)

data class ScheduledRunResult(
    val id: String = "",
    val sent: Int = 0,
    val failed: Int = 0,
    val refunded: Int = 0,
    val error: String? = null,
)

class SchedulerService {

    private val db = FirebaseFirestore.getInstance()

    // ============================================================
    //  CREATE
    // ============================================================
    suspend fun scheduleBlast(
        uid: String,
        userName: String,
        userEmail: String,
        target: String,
        message: String,
        count: Int,
        power: Int,
        speed: String,
        scheduledAt: Long,
        repeatMinutes: Int = 0,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (target.isBlank()) return@withContext Result.failure(Exception("Target required"))
            if (message.isBlank()) return@withContext Result.failure(Exception("Message required"))
            if (count <= 0) return@withContext Result.failure(Exception("Count must be > 0"))
            if (scheduledAt <= System.currentTimeMillis() / 1000L) {
                return@withContext Result.failure(Exception("Scheduled time must be in the future"))
            }

            val ref = db.collection(FirebaseConfig.COL_SCHEDULED).document()
            ref.set(
                mapOf(
                    "id" to ref.id,
                    "uid" to uid,
                    "userName" to userName,
                    "userEmail" to userEmail,
                    "target" to target,
                    "message" to message.take(1000),
                    "count" to count.toLong(),
                    "power" to power.toLong(),
                    "speed" to speed,
                    "scheduledAt" to scheduledAt,
                    "createdAt" to System.currentTimeMillis() / 1000L,
                    "status" to "pending",
                    "firedAt" to 0L,
                    "repeatMinutes" to repeatMinutes.toLong(),
                    "lastError" to "",
                )
            ).await()

            Result.success(ref.id)
        } catch (e: Exception) {
            Log.e(TAG, "scheduleBlast failed", e)
            Result.failure(e)
        }
    }

    // ============================================================
    //  LIST — user's own
    // ============================================================
    suspend fun listUserSchedules(uid: String, limit: Long = 50): List<ScheduledBlast> =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_SCHEDULED)
                    .whereEqualTo("uid", uid)
                    .whereIn("status", listOf("pending", "running"))
                    .orderBy("scheduledAt", Query.Direction.ASCENDING)
                    .limit(limit)
                    .get().await()
                    .documents.mapNotNull { parse(it.id, it.data ?: emptyMap()) }
            } catch (e: Exception) {
                Log.w(TAG, "listUserSchedules failed", e)
                emptyList()
            }
        }

    // ============================================================
    //  LIST — admin view of all pending
    // ============================================================
    suspend fun listAllPendingSchedules(limit: Long = 200): List<ScheduledBlast> =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_SCHEDULED)
                    .whereEqualTo("status", "pending")
                    .orderBy("scheduledAt", Query.Direction.ASCENDING)
                    .limit(limit)
                    .get().await()
                    .documents.mapNotNull { parse(it.id, it.data ?: emptyMap()) }
            } catch (e: Exception) {
                Log.w(TAG, "listAllPendingSchedules failed", e)
                emptyList()
            }
        }

    // ============================================================
    //  DUE — schedules whose time has come
    // ============================================================
    suspend fun getDueSchedules(): List<ScheduledBlast> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis() / 1000L
            db.collection(FirebaseConfig.COL_SCHEDULED)
                .whereEqualTo("status", "pending")
                .whereLessThanOrEqualTo("scheduledAt", now)
                .orderBy("scheduledAt", Query.Direction.ASCENDING)
                .limit(20)
                .get().await()
                .documents.mapNotNull { parse(it.id, it.data ?: emptyMap()) }
        } catch (e: Exception) {
            Log.w(TAG, "getDueSchedules failed", e)
            emptyList()
        }
    }

    // ============================================================
    //  MARK RUNNING — lock the schedule
    // ============================================================
    suspend fun markRunning(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            db.collection(FirebaseConfig.COL_SCHEDULED).document(id)
                .update(
                    "status", "running",
                    "firedAt", System.currentTimeMillis() / 1000L,
                ).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "markRunning failed", e)
            false
        }
    }

    // ============================================================
    //  COMPLETE — finished, no repeat
    // ============================================================
    suspend fun markComplete(id: String, result: ScheduledRunResult): Boolean =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_SCHEDULED).document(id)
                    .update(
                        mapOf(
                            "status" to "completed",
                            "lastError" to (result.error ?: ""),
                            "firedAt" to System.currentTimeMillis() / 1000L,
                        )
                    ).await()
                true
            } catch (e: Exception) {
                Log.w(TAG, "markComplete failed", e)
                false
            }
        }

    // ============================================================
    //  RESCHEDULE — for repeat
    // ============================================================
    suspend fun markRescheduled(id: String, nextAt: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            db.collection(FirebaseConfig.COL_SCHEDULED).document(id)
                .update(
                    mapOf(
                        "status" to "pending",
                        "scheduledAt" to nextAt,
                        "firedAt" to System.currentTimeMillis() / 1000L,
                    )
                ).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "markRescheduled failed", e)
            false
        }
    }

    // ============================================================
    //  CANCEL
    // ============================================================
    suspend fun cancelSchedule(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            db.collection(FirebaseConfig.COL_SCHEDULED).document(id)
                .update(
                    mapOf(
                        "status" to "cancelled",
                        "lastError" to "cancelled-by-user",
                    )
                ).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "cancelSchedule failed", e)
            false
        }
    }

    // ============================================================
    //  PARSE
    // ============================================================
    private fun parse(id: String, map: Map<String, Any?>): ScheduledBlast? {
        if (map.isEmpty()) return null
        return ScheduledBlast(
            id = id,
            uid = map["uid"] as? String ?: "",
            userName = map["userName"] as? String ?: "",
            userEmail = map["userEmail"] as? String ?: "",
            target = map["target"] as? String ?: "",
            message = map["message"] as? String ?: "",
            count = (map["count"] as? Number)?.toInt() ?: 0,
            power = (map["power"] as? Number)?.toInt() ?: 2,
            speed = map["speed"] as? String ?: "FAST",
            scheduledAt = (map["scheduledAt"] as? Number)?.toLong() ?: 0L,
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
            status = map["status"] as? String ?: "pending",
            firedAt = (map["firedAt"] as? Number)?.toLong() ?: 0L,
            repeatMinutes = (map["repeatMinutes"] as? Number)?.toInt() ?: 0,
            lastError = map["lastError"] as? String ?: "",
        )
    }

    companion object {
        private const val TAG = "SchedulerService"
    }
}