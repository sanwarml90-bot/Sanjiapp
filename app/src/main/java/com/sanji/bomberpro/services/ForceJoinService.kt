package com.sanji.bomberpro.services

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.sanji.bomberpro.FirebaseConfig
import com.sanji.bomberpro.data.ForceJoinChannel
import com.sanji.bomberpro.data.ForceJoinState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

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

    /** Always returns channels + bypass, regardless of enabled flag. For admin screen. */
    suspend fun listAll(): ForceJoinState = withContext(Dispatchers.IO) {
        try {
            val settingsDoc = settingsRef.get().await()
            val enabled = settingsDoc.getBoolean("forceJoinEnabled") ?: false

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

            ForceJoinState(enabled = enabled, channels = channels, bypass = bypass)
        } catch (e: Exception) {
            Log.w(TAG, "listAll failed", e)
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
            val snap = settingsRef.get().await()
            val bypass = snap.get("forceJoinBypass") as? List<*> ?: emptyList<Any>()
            bypass.any { it.toString() == uid }
        } catch (e: Exception) {
            false
        }
    }

    // ============================================================
    //  REQUEST VERIFICATION (user taps "I Joined")
    // ============================================================
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

                val userSnap = db.collection(FirebaseConfig.COL_USERS).document(uid).get().await()
                val verified = userSnap.getBoolean("forceJoinVerified") ?: false
                if (verified) {
                    try { reqRef.update("status", "verified").await() } catch (_: Exception) {}
                    return@withContext Result.success(true)
                }

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