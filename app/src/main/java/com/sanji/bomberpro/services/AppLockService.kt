package com.sanji.bomberpro.services

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.sanji.bomberpro.FirebaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class AppLockState(
    val locked: Boolean = false,
    val message: String = "",
    val apkUrl: String = "",
    val lockedAt: Long = 0L,
)

/**
 * Global app kill switch.
 *
 * Owner can lock the entire app from Admin panel. When locked:
 * - All users (non-owner) see a blocking screen with the message
 * - Owner is exempt
 * - Locking is reversible (owner can unlock)
 */
class AppLockService {

    private val db = FirebaseFirestore.getInstance()

    private val docRef get() = db.collection(FirebaseConfig.COL_SETTINGS)
        .document(FirebaseConfig.DOC_GLOBAL_SETTINGS)

    // ============================================================
    //  READ
    // ============================================================
    suspend fun getState(): AppLockState = withContext(Dispatchers.IO) {
        try {
            val snap = docRef.get().await()
            if (!snap.exists()) return@withContext AppLockState()
            AppLockState(
                locked = snap.getBoolean("appLocked") ?: false,
                message = snap.getString("appLockedMessage") ?: "",
                apkUrl = snap.getString("appLockedApkUrl") ?: "",
                lockedAt = snap.getLong("appLockedAt") ?: 0L,
            )
        } catch (e: Exception) {
            Log.w(TAG, "getState failed", e)
            AppLockState()
        }
    }

    // ============================================================
    //  LOCK — owner only
    // ============================================================
    suspend fun lockApp(
        ownerUid: String,
        message: String,
        apkUrl: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanMsg = message.trim().ifBlank {
                "This version of the app has been closed. Please update to continue."
            }.take(500)
            val cleanUrl = apkUrl.trim().take(500)
            val now = System.currentTimeMillis() / 1000L

            docRef.set(
                mapOf(
                    "appLocked" to true,
                    "appLockedMessage" to cleanMsg,
                    "appLockedApkUrl" to cleanUrl,
                    "appLockedAt" to now,
                    "appLockedBy" to ownerUid,
                ),
                SetOptions.merge(),
            ).await()

            try {
                db.collection(FirebaseConfig.COL_AUDIT_LOG).add(
                    mapOf(
                        "adminUid" to ownerUid,
                        "action" to "app_locked",
                        "targetUid" to "",
                        "amount" to 0L,
                        "reason" to cleanMsg.take(100),
                        "ts" to now,
                    )
                ).await()
            } catch (_: Exception) {}

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "lockApp failed", e)
            Result.failure(e)
        }
    }

    // ============================================================
    //  UNLOCK — owner only
    // ============================================================
    suspend fun unlockApp(ownerUid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            docRef.set(
                mapOf(
                    "appLocked" to false,
                    "appLockedMessage" to "",
                    "appLockedApkUrl" to "",
                    "appLockedAt" to 0L,
                ),
                SetOptions.merge(),
            ).await()

            try {
                db.collection(FirebaseConfig.COL_AUDIT_LOG).add(
                    mapOf(
                        "adminUid" to ownerUid,
                        "action" to "app_unlocked",
                        "targetUid" to "",
                        "amount" to 0L,
                        "reason" to "",
                        "ts" to System.currentTimeMillis() / 1000L,
                    )
                ).await()
            } catch (_: Exception) {}

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "unlockApp failed", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "AppLockService"
    }
}