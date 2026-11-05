package com.sanji.bomberpro.services

import com.sanji.bomberpro.data.GlobalSettings
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
        /** Credits the user will actually be billed at completion (base count × creditCost, admin = 0) */
        val chargeableBase: Int = 0,
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
        /** Credits actually charged to the user at end of run */
        val chargedCredits: Int,
        /** Credits refunded (reserved but not sent) */
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
     * Blast executor.
     *
     * BILLING MODEL:
     *  - User pays `count × creditCostPerSms` (base count ONLY).
     *  - Power is a FREE multiplier — sends go up to `count × power`, device-capped.
     *  - Admin never pays.
     *  - Credits are reserved upfront (= count × creditCostPerSms).
     *  - On completion, unsent portion is refunded → user pays only for what actually sent.
     *  - Balance NEVER goes negative.
     *
     * STOP BEHAVIOR:
     *  - cancelFlag.set(true) → in-flight sends finish, no new sends start.
     *  - Every send checks flag before dispatch.
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
        creditCostPerSms: Int = 1,
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
        //  BILLING SETUP
        //  desiredSends = count × power  (all sends we'll attempt)
        //  chargeableBase = count × creditCost  (what user pays IF all send)
        //  reservedCredits = chargeableBase  (hold upfront)
        //  deviceCap = devices × power  (can't physically send more than this)
        // -----------------------------------------------------------
        val safePower = power.coerceAtLeast(1)
        val safeCost = creditCostPerSms.coerceAtLeast(0)

        val desiredSends = count * safePower
        val deviceCap = devices.size * safePower
        val effectiveSends = min(desiredSends, deviceCap)

        // Max credits user could be charged (admin = 0)
        val maxChargeable = if (isAdmin) 0 else (count * safeCost).coerceAtLeast(0)

        // Reserve upfront — min(chargeable, current balance) for user
        val currentBalance = if (isAdmin) 0 else userData.getCredits(uid)
        val reservedCredits = if (isAdmin) 0 else min(maxChargeable, currentBalance)

        if (!isAdmin) {
            if (reservedCredits <= 0 || (safeCost > 0 && currentBalance < safeCost)) {
                trySend(BlastEvent.Error("Insufficient credits"))
                close()
                return@callbackFlow
            }
            if (!userData.forceDeductCredits(uid, reservedCredits, "blast-reserve")) {
                trySend(BlastEvent.Error("Credit reserve failed"))
                close()
                return@callbackFlow
            }
        }

        val total = effectiveSends
        val sent = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val startedAt = System.currentTimeMillis()
        val deviceLabels = devices.map { it.firebaseLabel }.distinct()

        // Max base units we'll ever charge — user pays at most this × costPerSms
        // Each "base unit" = one SMS equivalent at creditCostPerSms
        // e.g. count=10, cost=1, power=2 → maxChargeable=10 credits
        //      if we send 20 SMS, we still only charge 10 credits
        //      because those 10 base units cover the 20 power-extended sends.
        //
        // Charging formula at end:
        //   baseUnitsSent = min(count, ceil(sent / power))
        //   charged = baseUnitsSent × creditCostPerSms
        // But simpler & safer: charged = min(reservedCredits, actualSent × creditCostPerSms)
        //   — but that overcharges when power>1.
        //
        // BEST: charged = reservedCredits × (actualSent / total)  (proportional refund)
        //   BUT if user stopped early at 5/20 sent → refund half.
        //
        // FINAL FORMULA (safe, predictable, power-friendly):
        //   baseSentFraction = actualSent / effectiveSends
        //   charged = reservedCredits × baseSentFraction  (rounded down)
        //   refund  = reservedCredits - charged
        //
        // This means: user pays proportional to % of planned sends that succeeded.
        // Power = free. Credit math independent of power.

        trySend(
            BlastEvent.Started(
                total = total,
                devices = devices.size,
                reservedCredits = reservedCredits,
                powerMode = safePower,
                chargeableBase = maxChargeable,
            )
        )

        // -----------------------------------------------------------
        //  Per-device cooldown
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
                val jobs = (0 until total).map { i ->
                    async {
                        // Check before entering permit — huge win for instant stop
                        if (cancelFlag.get()) return@async

                        semaphore.withPermit {
                            // Check again inside permit — critical
                            if (cancelFlag.get()) return@withPermit
                            if (sent.get() + failed.get() >= total) return@withPermit

                            val device = devices[i % devices.size]
                            val passNum = i / devices.size

                            // Cooldown
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

                            // One more cancel check before actual network call
                            if (cancelFlag.get()) return@withPermit

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

                // -------------------------------------------------
                //  BILLING — proportional charge
                //  charged = reservedCredits × (sent / total)
                //  refund  = reservedCredits - charged
                //  (never negative, always ≤ reserved)
                // -------------------------------------------------
                val chargedCredits: Int = if (isAdmin) {
                    0
                } else if (total <= 0) {
                    0
                } else {
                    val fraction = actualSent.toDouble() / total.toDouble()
                    val raw = (reservedCredits * fraction).toInt()
                    raw.coerceIn(0, reservedCredits)
                }

                val refundAmount: Int = if (isAdmin) 0 else {
                    (reservedCredits - chargedCredits).coerceAtLeast(0)
                }

                if (refundAmount > 0) {
                    try {
                        userData.addCredits(uid, refundAmount, "blast-refund")
                    } catch (_: Exception) {}
                }

                val finalBalance = if (isAdmin) 0 else userData.getCredits(uid)

                // -------------------------------------------------
                //  HISTORY
                // -------------------------------------------------
                try {
                    userData.saveBlastHistory(
                        uid = uid,
                        targetFull = target,
                        message = message,
                        requested = total,
                        sent = actualSent,
                        failed = actualFailed,
                        durationSec = duration,
                        speed = "${speed.label} ${safePower}x",
                        devicePool = deviceLabels,
                        status = if (wasCancelled) "stopped" else "completed",
                    )
                } catch (_: Exception) {}

                // -------------------------------------------------
                //  ACTIVITY LOG
                // -------------------------------------------------
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
                            "power" to safePower,
                            "duration" to duration,
                            "devicePool" to deviceLabels,
                            "reserved" to reservedCredits,
                            "charged" to chargedCredits,
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
                        chargedCredits = chargedCredits,
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
     * SIM slot rotation — matches bot behavior.
     */
    private fun simSlotFor(device: Device, passNum: Int): Int {
        if (device.sims.isEmpty()) return 0
        val idx = passNum % device.sims.size
        val first = device.sims[idx]
        return (first["simSlotIndex"] as? Int) ?: 0
    }
}