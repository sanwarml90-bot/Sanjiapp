package com.sanji.bomberpro.services

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.sanji.bomberpro.FirebaseConfig
import com.sanji.bomberpro.data.DeviceLock
import com.sanji.bomberpro.data.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * One-device-one-account enforcement.
 *
 * Every device gets a stable fingerprint = sha256(androidId + buildFingerprint + serialBestEffort).
 * Fingerprint is stored in `device_locks/{fingerprint}` and mapped to a single uid.
 *
 * Rules:
 *  - USER role: locked to one device. Second login from different account → blocked.
 *  - ADMIN / SUPER_ADMIN / OWNER: exempt — can login on any device, any number of accounts.
 *
 * On successful login (via AuthManager), call `registerDevice` with the user's role.
 * If registration fails due to collision, block login.
 * On account delete, call `releaseDevice`.
 *
 * Also writes fingerprint into `users/{uid}.deviceInfo.fingerprint` for admin analysis.
 */
class DeviceLockService(private val context: Context) {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    // ============================================================
    //  FINGERPRINT
    // ============================================================
    /**
     * Stable per-device fingerprint. Survives app reinstall (for most devices).
     * Changes on factory reset — that's acceptable, means new device slot.
     */
    @SuppressLint("HardwareIds")
    fun getDeviceFingerprint(): String {
        val androidId = try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID,
            ) ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }

        val buildFp = try {
            Build.FINGERPRINT ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }

        val serial = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Build.getSerial()
            } else {
                @Suppress("DEPRECATION")
                Build.SERIAL
            }
        } catch (_: Exception) {
            "unknown"
        }

        val model = try {
            "${Build.MANUFACTURER}-${Build.MODEL}-${Build.DEVICE}"
        } catch (_: Exception) {
            "unknown"
        }

        val raw = "$androidId|$buildFp|$serial|$model"
        return sha256(raw)
    }

    private fun sha256(input: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
            val sb = StringBuilder()
            for (b in bytes) sb.append(String.format("%02x", b))
            sb.toString()
        } catch (_: Exception) {
            input.hashCode().toString()
        }
    }

    // ============================================================
    //  READ
    // ============================================================
    suspend fun getLock(fingerprint: String): DeviceLock? = withContext(Dispatchers.IO) {
        try {
            val snap = db.collection(FirebaseConfig.COL_DEVICE_LOCKS)
                .document(fingerprint).get().await()
            if (!snap.exists()) return@withContext null
            DeviceLock(
                fingerprint = fingerprint,
                uid = snap.getString("uid") ?: "",
                email = snap.getString("email") ?: "",
                userName = snap.getString("userName") ?: "",
                deviceModel = snap.getString("deviceModel") ?: "",
                deviceBrand = snap.getString("deviceBrand") ?: "",
                androidVersion = snap.getString("androidVersion") ?: "",
                sdkInt = (snap.getLong("sdkInt") ?: 0L).toInt(),
                createdAt = snap.getLong("createdAt") ?: 0L,
                updatedAt = snap.getLong("updatedAt") ?: 0L,
            )
        } catch (e: Exception) {
            Log.w(TAG, "getLock failed", e)
            null
        }
    }

    /**
     * Is this device already locked to a DIFFERENT uid?
     * Returns the OTHER uid if locked, or null if free / locked to same uid / admin.
     */
    suspend fun isDeviceLockedToOtherUid(
        fingerprint: String,
        uid: String,
        role: UserRole,
    ): String? = withContext(Dispatchers.IO) {
        // Admin tiers are exempt from device locking
        if (role.priority >= UserRole.ADMIN.priority) return@withContext null

        try {
            val snap = db.collection(FirebaseConfig.COL_DEVICE_LOCKS)
                .document(fingerprint).get().await()
            if (!snap.exists()) return@withContext null
            val lockedUid = snap.getString("uid") ?: return@withContext null
            if (lockedUid.isBlank()) return@withContext null
            if (lockedUid == uid) return@withContext null
            lockedUid
        } catch (e: Exception) {
            Log.w(TAG, "isDeviceLockedToOtherUid failed", e)
            // Fail open — don't block users if Firestore is unreachable
            null
        }
    }

    // ============================================================
    //  REGISTER
    // ============================================================
    /**
     * Register this device to `uid`.
     *
     * Behavior:
     *  - ADMIN+ : always succeeds. Also writes device fingerprint to user doc for analysis.
     *  - USER   : if device not locked → lock it to uid. If locked to same uid → touch timestamp.
     *             If locked to another uid → returns failure with the conflicting uid.
     *
     * Also always writes `users/{uid}.deviceInfo.fingerprint` so admin analysis can group users.
     */
    suspend fun registerDevice(
        uid: String,
        email: String,
        userName: String,
        role: UserRole,
        fingerprintOverride: String? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val fingerprint = fingerprintOverride ?: getDeviceFingerprint()
            if (fingerprint.isBlank()) {
                return@withContext Result.failure(Exception("Fingerprint unavailable"))
            }

            val isAdminTier = role.priority >= UserRole.ADMIN.priority
            val now = System.currentTimeMillis() / 1000L

            // Always record device fingerprint on user doc (for admin analysis)
            try {
                db.collection(FirebaseConfig.COL_USERS).document(uid)
                    .set(
                        mapOf(
                            "deviceInfo.fingerprint" to fingerprint,
                            "deviceInfo.fingerprintReportedAt" to now,
                        ),
                        SetOptions.merge(),
                    ).await()
            } catch (_: Exception) {}

            if (isAdminTier) {
                // Admin: don't create device_lock — keeps them free. Just return success.
                return@withContext Result.success(Unit)
            }

            // USER: enforce 1:1
            val lockRef = db.collection(FirebaseConfig.COL_DEVICE_LOCKS).document(fingerprint)
            val existingSnap = lockRef.get().await()

            if (existingSnap.exists()) {
                val lockedUid = existingSnap.getString("uid") ?: ""
                if (lockedUid.isNotBlank() && lockedUid != uid) {
                    // Device already tied to someone else
                    return@withContext Result.failure(
                        Exception("DEVICE_LOCKED:$lockedUid")
                    )
                }
                // Same uid — refresh metadata
                lockRef.set(
                    mapOf(
                        "fingerprint" to fingerprint,
                        "uid" to uid,
                        "email" to email,
                        "userName" to userName.take(64),
                        "deviceModel" to "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                        "deviceBrand" to Build.BRAND,
                        "androidVersion" to Build.VERSION.RELEASE,
                        "sdkInt" to Build.VERSION.SDK_INT.toLong(),
                        "updatedAt" to now,
                    ),
                    SetOptions.merge(),
                ).await()
                Result.success(Unit)
            } else {
                // Fresh lock — reserve it for this uid
                lockRef.set(
                    mapOf(
                        "fingerprint" to fingerprint,
                        "uid" to uid,
                        "email" to email,
                        "userName" to userName.take(64),
                        "deviceModel" to "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                        "deviceBrand" to Build.BRAND,
                        "androidVersion" to Build.VERSION.RELEASE,
                        "sdkInt" to Build.VERSION.SDK_INT.toLong(),
                        "createdAt" to now,
                        "updatedAt" to now,
                    )
                ).await()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.w(TAG, "registerDevice failed", e)
            // Fail open — don't block login on network error
            Result.success(Unit)
        }
    }

    // ============================================================
    //  RELEASE
    // ============================================================
    /**
     * Release this device's lock. Called on account deletion.
     * Also called by admin if we ever need to "reset" a device.
     */
    suspend fun releaseDevice(fingerprintOverride: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val fingerprint = fingerprintOverride ?: getDeviceFingerprint()
                if (fingerprint.isBlank()) return@withContext false
                db.collection(FirebaseConfig.COL_DEVICE_LOCKS)
                    .document(fingerprint).delete().await()
                true
            } catch (e: Exception) {
                Log.w(TAG, "releaseDevice failed", e)
                false
            }
        }

    /**
     * Release any lock that points to `uid`. Used on account deletion
     * when we can't recompute the fingerprint (different device context).
     */
    suspend fun releaseAllLocksForUid(uid: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val locks = db.collection(FirebaseConfig.COL_DEVICE_LOCKS)
                .whereEqualTo("uid", uid).get().await().documents
            val batch = db.batch()
            for (l in locks) batch.delete(l.reference)
            if (locks.isNotEmpty()) batch.commit().await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "releaseAllLocksForUid failed", e)
            false
        }
    }

    // ============================================================
    //  ADMIN
    // ============================================================
    suspend fun getAllLocks(limit: Long = 500): List<DeviceLock> = withContext(Dispatchers.IO) {
        try {
            db.collection(FirebaseConfig.COL_DEVICE_LOCKS)
                .limit(limit)
                .get().await()
                .documents.mapNotNull { d ->
                    DeviceLock(
                        fingerprint = d.id,
                        uid = d.getString("uid") ?: "",
                        email = d.getString("email") ?: "",
                        userName = d.getString("userName") ?: "",
                        deviceModel = d.getString("deviceModel") ?: "",
                        deviceBrand = d.getString("deviceBrand") ?: "",
                        androidVersion = d.getString("androidVersion") ?: "",
                        sdkInt = (d.getLong("sdkInt") ?: 0L).toInt(),
                        createdAt = d.getLong("createdAt") ?: 0L,
                        updatedAt = d.getLong("updatedAt") ?: 0L,
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Admin override: force-release a lock by fingerprint.
     * Used if a user's device is stuck and they can't log in.
     */
    suspend fun adminForceRelease(fingerprint: String): Boolean = releaseDevice(fingerprint)

    companion object {
        private const val TAG = "DeviceLockService"
    }
}