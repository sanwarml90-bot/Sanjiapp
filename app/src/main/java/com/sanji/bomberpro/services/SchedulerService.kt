package com.sanji.bomberpro.services

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
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

/**
 * Scheduled blasts.
 *
 * FIXED: All queries now use SINGLE-FIELD filters + CLIENT-SIDE filter/sort
 * to avoid Firestore composite index requirements. Old code used
 * whereEqualTo + whereLessThanOrEqualTo + orderBy on multiple fields,
 * which silently returned empty without a deployed index.
 *
 * NOTE: SchedulerTicker runs only while app is open. For schedules to fire,
 * the app must be foregrounded or recently backgrounded (process alive).
 * For 24/7 reliability, keep the app open or use a bot.
 */
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
    //  LIST — user's own schedules (client-side filter + sort)
    // ============================================================
    suspend fun listUserSchedules(uid: String, limit: Long = 50): List<ScheduledBlast> =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_SCHEDULED)
                    .whereEqualTo("uid", uid)
                    .limit(200)
                    .get().await()
                    .documents
                    .mapNotNull { parse(it.id, it.data ?: emptyMap()) }
                    .filter { it.status == "pending" || it.status == "running" }
                    .sortedBy { it.scheduledAt }
                    .take(limit.toInt())
            } catch (e: Exception) {
                Log.e(TAG, "listUserSchedules failed", e)
                emptyList()
            }
        }

    // ============================================================
    //  LIST — all pending (admin, client-side filter + sort)
    // ============================================================
    suspend fun listAllPendingSchedules(limit: Long = 200): List<ScheduledBlast> =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_SCHEDULED)
                    .whereEqualTo("status", "pending")
                    .limit(500)
                    .get().await()
                    .documents
                    .mapNotNull { parse(it.id, it.data ?: emptyMap()) }
                    .sortedBy { it.scheduledAt }
                    .take(limit.toInt())
            } catch (e: Exception) {
                Log.e(TAG, "listAllPendingSchedules failed", e)
                emptyList()
            }
        }

    // ============================================================
    //  DUE — schedules whose time has come (client-side filter)
    // ============================================================
    suspend fun getDueSchedules(): List<ScheduledBlast> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis() / 1000L
            db.collection(FirebaseConfig.COL_SCHEDULED)
                .whereEqualTo("status", "pending")
                .limit(200)
                .get().await()
                .documents
                .mapNotNull { parse(it.id, it.data ?: emptyMap()) }
                .filter { it.scheduledAt in 1..now }
                .sortedBy { it.scheduledAt }
                .take(20)
        } catch (e: Exception) {
            Log.e(TAG, "getDueSchedules failed", e)
            emptyList()
        }
    }

    // ============================================================
    //  MARK RUNNING
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
            Log.e(TAG, "markRunning failed", e)
            false
        }
    }

    // ============================================================
    //  MARK COMPLETE
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
                Log.e(TAG, "markComplete failed", e)
                false
            }
        }

    // ============================================================
    //  RESCHEDULE (for repeat)
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
            Log.e(TAG, "markRescheduled failed", e)
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
            Log.e(TAG, "cancelSchedule failed", e)
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