package com.sanji.bomberpro.services

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.sanji.bomberpro.FirebaseConfig
import com.sanji.bomberpro.data.ForceJoinChannel
import com.sanji.bomberpro.data.ForceJoinState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Force-join gating.
 *
 * MODE: SELF-ATTEST (bot optional)
 *  - User taps JOIN on each channel → opens Telegram
 *  - User taps "I'VE JOINED ALL" → we immediately mark verified. No 20s wait.
 *  - A deployed bot MAY later verify and set forceJoinMemberOf. If it finds
 *    the user didn't actually join, it can flip forceJoinVerified=false
 *    (admin can also reset).
 *  - Priority is: instant access, no friction.
 */
class ForceJoinService {

    private val db = FirebaseFirestore.getInstance()

    private val settingsRef get() = db.collection(FirebaseConfig.COL_SETTINGS)
        .document(FirebaseConfig.DOC_GLOBAL_SETTINGS)

    // ============================================================
    //  CONFIG READ
    // ============================================================
    suspend fun loadForceJoinState(): ForceJoinState = withContext(Dispatchers.IO) {
        try {
            val settingsDoc = settingsRef.get().await()
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

            ForceJoinState(enabled = true, channels = channels, bypass = bypass)
        } catch (e: Exception) {
            Log.w(TAG, "loadForceJoinState failed", e)
            ForceJoinState(enabled = false)
        }
    }

    suspend fun listAll(): ForceJoinState = withContext(Dispatchers.IO) {
        try {
            val settingsDoc = settingsRef.get().await()
            val enabled = settingsDoc.getBoolean("forceJoinEnabled") ?: false

            val channelsSnap = db.collection(FirebaseConfig.COL_FORCE_JOIN).get().await()
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

            ForceJoinState(enabled = enabled, channels = channels, bypass = bypass)
        } catch (e: Exception) {
            Log.w(TAG, "listAll failed", e)
            ForceJoinState(enabled = false)
        }
    }

    // ============================================================
    //  USER STATUS
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
            val snap = settingsRef.get().await()
            val bypass = snap.get("forceJoinBypass") as? List<*> ?: emptyList<Any>()
            bypass.any { it.toString() == uid }
        } catch (e: Exception) {
            false
        }
    }

    // ============================================================
    //  INSTANT VERIFY — tap "I've joined" → access
    // ============================================================
    /**
     * Called when user taps "I'VE JOINED ALL".
     *
     * Immediately:
     *   1. Writes `forceJoinVerified=true` to user doc
     *   2. Records timestamp + self-attest flag
     *   3. Queues a `join_check_requests` doc for any deployed bot
     *      to optionally validate async (result ignored for access)
     *
     * Returns success in <200ms. No polling. No timeout.
     */
    suspend fun requestVerification(
        uid: String,
        userName: String,
        timeoutSeconds: Int = 0, // kept for API compat, ignored
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (uid.isBlank()) return@withContext Result.failure(Exception("Missing uid"))

            val now = System.currentTimeMillis() / 1000L

            // Instant mark — self-attest
            db.collection(FirebaseConfig.COL_USERS).document(uid)
                .update(
                    mapOf(
                        "forceJoinVerified" to true,
                        "forceJoinVerifiedAt" to now,
                        "forceJoinSelfClaimed" to true,
                    )
                ).await()

            // Async check queue — a deployed bot MAY verify or revoke
            try {
                val reqRef = db.collection("join_check_requests").document()
                reqRef.set(
                    mapOf(
                        "uid" to uid,
                        "userName" to userName.take(64),
                        "requestedAt" to now,
                        "status" to "self_attested",
                        "source" to "app-tap",
                    ),
                    SetOptions.merge(),
                ).await()
            } catch (_: Exception) {}

            Result.success(true)
        } catch (e: Exception) {
            Log.w(TAG, "requestVerification failed", e)
            // Fail-open — allow access, retry next launch
            Result.success(true)
        }
    }

    /**
     * Admin / bot may revoke self-attested verification if user didn't actually join.
     */
    suspend fun revokeVerification(uid: String, reason: String = ""): Boolean =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_USERS).document(uid)
                    .update(
                        mapOf(
                            "forceJoinVerified" to false,
                            "forceJoinRevokedAt" to System.currentTimeMillis() / 1000L,
                            "forceJoinRevokeReason" to reason.take(200),
                        )
                    ).await()
                true
            } catch (_: Exception) { false }
        }

    // ============================================================
    //  LIST MISSING CHANNELS
    // ============================================================
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

    // ============================================================
    //  ADMIN WRITE — CHANNELS
    // ============================================================
    suspend fun addChannel(
        id: String,
        title: String,
        link: String,
        required: Boolean = true,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanId = id.trim()
            val cleanLink = link.trim()
            val cleanTitle = title.trim().take(80)
            if (cleanId.isBlank()) return@withContext Result.failure(Exception("Channel ID required"))
            if (cleanLink.isBlank() || !cleanLink.startsWith("http")) {
                return@withContext Result.failure(Exception("Valid link required (must start with http)"))
            }
            val existing = db.collection(FirebaseConfig.COL_FORCE_JOIN)
                .whereEqualTo("id", cleanId)
                .limit(1).get().await()
            if (!existing.isEmpty) {
                return@withContext Result.failure(Exception("Channel already added"))
            }
            val ref = db.collection(FirebaseConfig.COL_FORCE_JOIN).document()
            ref.set(
                mapOf(
                    "id" to cleanId,
                    "title" to cleanTitle.ifBlank { cleanId },
                    "link" to cleanLink,
                    "required" to required,
                    "addedAt" to System.currentTimeMillis() / 1000L,
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeChannel(channelId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val snap = db.collection(FirebaseConfig.COL_FORCE_JOIN)
                .whereEqualTo("id", channelId)
                .limit(1).get().await()
            if (snap.isEmpty) return@withContext false
            snap.documents.first().reference.delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setForceJoinEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            settingsRef.set(
                mapOf("forceJoinEnabled" to enabled),
                SetOptions.merge(),
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ============================================================
    //  ADMIN WRITE — BYPASS
    // ============================================================
    suspend fun addBypass(uid: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val clean = uid.trim()
            if (clean.isBlank()) return@withContext false
            settingsRef.set(
                mapOf("forceJoinBypass" to FieldValue.arrayUnion(clean)),
                SetOptions.merge(),
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun removeBypass(uid: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val clean = uid.trim()
            if (clean.isBlank()) return@withContext false
            settingsRef.set(
                mapOf("forceJoinBypass" to FieldValue.arrayRemove(clean)),
                SetOptions.merge(),
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getBypassList(): List<String> = withContext(Dispatchers.IO) {
        try {
            val snap = settingsRef.get().await()
            val bypass = snap.get("forceJoinBypass") as? List<*> ?: emptyList<Any>()
            bypass.map { it.toString() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val TAG = "ForceJoinService"
    }
}