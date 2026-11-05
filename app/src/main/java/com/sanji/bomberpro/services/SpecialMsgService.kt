package com.sanji.bomberpro.services

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.sanji.bomberpro.FirebaseConfig
import com.sanji.bomberpro.data.SpecialMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Full-page special message system.
 *
 * Owner publishes a message via admin. When published, `version` is bumped.
 * Users see it full-screen on next app open (after terms, before force-join).
 * Tapping X marks `specialMsgVersionSeen = version` → won't show again
 * until owner publishes a new version.
 *
 * Storage: special_msg/current
 */
class SpecialMsgService {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    private fun docRef() = db.collection(FirebaseConfig.COL_SPECIAL_MSG)
        .document(FirebaseConfig.DOC_CURRENT_SPECIAL_MSG)

    // ============================================================
    //  READ
    // ============================================================
    suspend fun getActiveMessage(): SpecialMessage? = withContext(Dispatchers.IO) {
        try {
            val snap = docRef().get().await()
            if (!snap.exists()) return@withContext null
            val enabled = snap.getBoolean("enabled") ?: false
            if (!enabled) return@withContext null
            SpecialMessage(
                id = snap.id,
                version = (snap.getLong("version") ?: 0L).toInt(),
                title = snap.getString("title") ?: "",
                body = snap.getString("body") ?: "",
                bodyHtml = snap.getString("bodyHtml") ?: "",
                isHtml = snap.getBoolean("isHtml") ?: false,
                enabled = true,
                publishedBy = snap.getString("publishedBy") ?: "",
                publishedAt = snap.getLong("publishedAt") ?: 0L,
                buttonText = snap.getString("buttonText") ?: "",
                buttonUrl = snap.getString("buttonUrl") ?: "",
            )
        } catch (e: Exception) {
            Log.w(TAG, "getActiveMessage failed", e)
            null
        }
    }

    /**
     * Should this user see the message?
     * True if: enabled, version > 0, and user's seen version < current.
     */
    suspend fun shouldShowForUser(
        uid: String,
        currentVersion: Int,
        userSeenVersion: Int,
    ): Boolean {
        if (currentVersion <= 0) return false
        if (uid.isBlank()) return false
        return userSeenVersion < currentVersion
    }

    /**
     * Marks the current version as seen by user. Called when X pressed.
     */
    suspend fun markSeen(uid: String, version: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            db.collection(FirebaseConfig.COL_USERS).document(uid)
                .update("specialMsgVersionSeen", version.toLong()).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ============================================================
    //  PUBLISH (owner)
    // ============================================================
    /**
     * Publishes (or overwrites) the special message.
     * Bumps version → users see again even if they dismissed previous.
     */
    suspend fun publishMessage(
        adminUid: String,
        title: String,
        bodyPlain: String,
        bodyHtml: String,
        isHtml: Boolean,
        enabled: Boolean,
        buttonText: String = "",
        buttonUrl: String = "",
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (title.isBlank()) return@withContext Result.failure(Exception("Title required"))
            val bodyContent = if (isHtml) bodyHtml else bodyPlain
            if (bodyContent.isBlank()) {
                return@withContext Result.failure(Exception("Body required"))
            }

            val currentSnap = docRef().get().await()
            val currentVersion = (currentSnap.getLong("version") ?: 0L).toInt()
            val newVersion = currentVersion + 1
            val now = System.currentTimeMillis() / 1000L

            docRef().set(
                mapOf(
                    "id" to FirebaseConfig.DOC_CURRENT_SPECIAL_MSG,
                    "version" to newVersion.toLong(),
                    "title" to title.trim().take(120),
                    "body" to bodyPlain.trim().take(5000),
                    "bodyHtml" to bodyHtml.trim().take(8000),
                    "isHtml" to isHtml,
                    "enabled" to enabled,
                    "publishedBy" to adminUid,
                    "publishedAt" to now,
                    "buttonText" to buttonText.trim().take(40),
                    "buttonUrl" to buttonUrl.trim().take(500),
                ),
                SetOptions.merge(),
            ).await()

            // Bump version in global settings too (for nav graph check)
            try {
                db.collection(FirebaseConfig.COL_SETTINGS)
                    .document(FirebaseConfig.DOC_GLOBAL_SETTINGS)
                    .set(
                        mapOf(
                            "specialMsgEnabled" to enabled,
                            "specialMsgVersion" to newVersion.toLong(),
                        ),
                        SetOptions.merge(),
                    ).await()
            } catch (_: Exception) {}

            // Audit
            try {
                db.collection(FirebaseConfig.COL_AUDIT_LOG).add(
                    mapOf(
                        "adminUid" to adminUid,
                        "action" to if (enabled) "special_msg_publish" else "special_msg_disable",
                        "targetUid" to "",
                        "amount" to newVersion.toLong(),
                        "reason" to title.take(80),
                        "ts" to now,
                    )
                ).await()
            } catch (_: Exception) {}

            Result.success(newVersion)
        } catch (e: Exception) {
            Log.e(TAG, "publishMessage failed", e)
            Result.failure(e)
        }
    }

    /**
     * Disables the current message. Users won't see it again.
     * Keeps data but flips enabled=false.
     */
    suspend fun disableMessage(adminUid: String): Boolean = withContext(Dispatchers.IO) {
        try {
            docRef().set(
                mapOf("enabled" to false),
                SetOptions.merge(),
            ).await()

            try {
                db.collection(FirebaseConfig.COL_SETTINGS)
                    .document(FirebaseConfig.DOC_GLOBAL_SETTINGS)
                    .set(
                        mapOf("specialMsgEnabled" to false),
                        SetOptions.merge(),
                    ).await()
            } catch (_: Exception) {}

            try {
                db.collection(FirebaseConfig.COL_AUDIT_LOG).add(
                    mapOf(
                        "adminUid" to adminUid,
                        "action" to "special_msg_disable",
                        "targetUid" to "",
                        "amount" to 0L,
                        "reason" to "disabled",
                        "ts" to System.currentTimeMillis() / 1000L,
                    )
                ).await()
            } catch (_: Exception) {}

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Full reset — wipes the message entirely.
     */
    suspend fun deleteMessage(adminUid: String): Boolean = withContext(Dispatchers.IO) {
        try {
            docRef().delete().await()

            try {
                db.collection(FirebaseConfig.COL_SETTINGS)
                    .document(FirebaseConfig.DOC_GLOBAL_SETTINGS)
                    .set(
                        mapOf(
                            "specialMsgEnabled" to false,
                            "specialMsgVersion" to 0L,
                        ),
                        SetOptions.merge(),
                    ).await()
            } catch (_: Exception) {}

            true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "SpecialMsgService"
    }
}