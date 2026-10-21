package com.sanji.bomberpro.services

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.sanji.bomberpro.FirebaseConfig
import com.sanji.bomberpro.data.ForceJoinChannel
import com.sanji.bomberpro.data.ForceJoinState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Force-join gate logic.
 *
 * Reality: only the bot (with admin rights in those channels) can verify
 * real Telegram membership. The app cannot check membership directly.
 *
 * Flow:
 *  1. App reads force_join config from Firestore (settings/global.forceJoinEnabled +
 *     force_join/channels collection).
 *  2. If enabled, user is shown Join buttons → deep-links to Telegram.
 *  3. User taps "I Joined" → app writes join_check_request/{uid}.
 *  4. Bot (payment_bot.py or main bot) listens, verifies membership for each
 *     channel via Telegram Bot API, writes users/{uid}.forceJoinVerified = true/false.
 *  5. App polls users/{uid}.forceJoinVerified every 2s for up to 20s.
 *  6. If verified → proceed. If not → show "not joined yet, try again".
 *
 * If bot doesn't have the verification listener running, user is stuck.
 * To avoid that, admin can:
 *  - leave forceJoinEnabled = false (default)
 *  - or manually set forceJoinVerified = true for a user
 */
class ForceJoinService {

    private val db = FirebaseFirestore.getInstance()

    // ============================================================
    //  CONFIG READ
    // ============================================================
    suspend fun loadForceJoinState(): ForceJoinState = withContext(Dispatchers.IO) {
        try {
            val settingsDoc = db.collection(FirebaseConfig.COL_SETTINGS)
                .document(FirebaseConfig.DOC_GLOBAL_SETTINGS)
                .get().await()

            val enabled = settingsDoc.getBoolean("forceJoinEnabled") ?: false
            if (!enabled) {
                return@withContext ForceJoinState(enabled = false)
            }

            val channelsSnap = db.collection(FirebaseConfig.COL_FORCE_JOIN)
                .get().await()

            val channels = channelsSnap.documents.mapNotNull { d ->
                val id = d.getString("id") ?: d.id
                val title = d.getString("title") ?: ""
                val link = d.getString("link") ?: ""
                if (id.isBlank() || link.isBlank()) return@mapNotNull null
                ForceJoinChannel(
                    id = id,
                    title = title,
                    link = link,
                    required = d.getBoolean("required") ?: true,
                )
            }

            val bypassSnap = settingsDoc.get("forceJoinBypass") as? List<*> ?: emptyList<Any>()
            val bypass = bypassSnap.map { it.toString() }

            ForceJoinState(
                enabled = true,
                channels = channels,
                bypass = bypass,
            )
        } catch (e: Exception) {
            Log.w(TAG, "loadForceJoinState failed", e)
            ForceJoinState(enabled = false)
        }
    }

    // ============================================================
    //  CURRENT USER FLAG
    // ============================================================
    suspend fun isUserVerified(uid: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val snap = db.collection(FirebaseConfig.COL_USERS).document(uid).get().await()
            snap.getBoolean("forceJoinVerified") ?: false
        } catch (e: Exception) {
            Log.w(TAG, "isUserVerified failed", e)
            false
        }
    }

    suspend fun isUserBypassed(uid: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val snap = db.collection(FirebaseConfig.COL_SETTINGS)
                .document(FirebaseConfig.DOC_GLOBAL_SETTINGS)
                .get().await()
            val bypass = snap.get("forceJoinBypass") as? List<*> ?: emptyList<Any>()
            bypass.any { it.toString() == uid }
        } catch (e: Exception) {
            false
        }
    }

    // ============================================================
    //  REQUEST VERIFICATION (user taps "I Joined")
    // ============================================================
    /**
     * Writes a check request. Bot picks it up, verifies, writes back
     * `users/{uid}.forceJoinVerified`.
     *
     * Returns true only if bot verified within `timeoutSeconds`.
     * If no bot listener is running, times out after timeout and returns false.
     */
    suspend fun requestVerification(
        uid: String,
        userName: String,
        timeoutSeconds: Int = 20,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val reqRef = db.collection("join_check_requests").document()
            reqRef.set(
                mapOf(
                    "uid" to uid,
                    "userName" to userName,
                    "requestedAt" to System.currentTimeMillis() / 1000L,
                    "status" to "pending",
                ),
                SetOptions.merge(),
            ).await()

            val deadline = System.currentTimeMillis() + timeoutSeconds * 1000L

            while (System.currentTimeMillis() < deadline) {
                delay(2000)

                // check user doc
                val userSnap = db.collection(FirebaseConfig.COL_USERS).document(uid).get().await()
                val verified = userSnap.getBoolean("forceJoinVerified") ?: false
                if (verified) {
                    try { reqRef.update("status", "verified").await() } catch (_: Exception) {}
                    return@withContext Result.success(true)
                }

                // check request doc for rejection
                val reqSnap = reqRef.get().await()
                val reqStatus = reqSnap.getString("status") ?: "pending"
                if (reqStatus == "rejected") {
                    return@withContext Result.success(false)
                }
            }

            try { reqRef.update("status", "timeout").await() } catch (_: Exception) {}
            Result.success(false)
        } catch (e: Exception) {
            Log.w(TAG, "requestVerification failed", e)
            Result.failure(e)
        }
    }

    // ============================================================
    //  LIST MISSING CHANNELS (for UI display)
    // ============================================================
    /**
     * Bot tracks per-channel membership in `users/{uid}.forceJoinMemberOf` array.
     * App compares against the configured channel list to show which to join.
     *
     * If bot never populated that field, returns all channels as "join".
     */
    suspend fun getMissingChannels(uid: String, all: List<ForceJoinChannel>): List<ForceJoinChannel> =
        withContext(Dispatchers.IO) {
            try {
                val snap = db.collection(FirebaseConfig.COL_USERS).document(uid).get().await()
                val memberOf = (snap.get("forceJoinMemberOf") as? List<*>)?.map { it.toString() } ?: emptyList()
                if (memberOf.isEmpty()) return@withContext all
                all.filter { ch -> ch.id !in memberOf }
            } catch (e: Exception) {
                all
            }
        }

    companion object {
        private const val TAG = "ForceJoinService"
    }
}