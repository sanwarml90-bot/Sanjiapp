package com.sanji.bomberpro.services

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-process giveaway ticker.
 * Runs while the app is open. Every 60s checks for expired active giveaways
 * and auto-ends them (distributes prizes + participation + queues channel post).
 *
 * Only ends giveaways — does not announce (bot handles channel posting via
 * giveaway_announce_requests queue in Firestore).
 */
class GiveawayTicker(private val scope: CoroutineScope) {

    private val service = GiveawayService()
    private var job: Job? = null
    private val running = AtomicBoolean(false)
    private val endedThisSession = mutableSetOf<String>()

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    tickOnce()
                } catch (e: Exception) {
                    Log.w(TAG, "tick failed", e)
                }
                delay(60_000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun tickOnce() {
        val active = service.listActive()
        if (active.isEmpty()) return

        val now = System.currentTimeMillis() / 1000L
        for (gw in active) {
            if (gw.endsAt > now) continue
            if (gw.id in endedThisSession) continue

            if (!running.compareAndSet(false, true)) return
            try {
                val res = service.endGiveaway(
                    gwId = gw.id,
                    adminUid = "auto",
                    requestChannelPost = true,
                )
                if (res.isSuccess) {
                    endedThisSession.add(gw.id)
                    Log.i(TAG, "auto-ended giveaway ${gw.id}")
                } else {
                    Log.w(TAG, "auto-end failed ${gw.id}: ${res.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "auto-end exception ${gw.id}", e)
            } finally {
                running.set(false)
            }
        }
    }

    companion object {
        private const val TAG = "GiveawayTicker"
    }
}