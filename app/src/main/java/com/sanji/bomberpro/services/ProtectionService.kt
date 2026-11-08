package com.sanji.bomberpro.services

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.sanji.bomberpro.FirebaseConfig
import com.sanji.bomberpro.data.ProtectedNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Number protection system.
 *
 * - Premium users and owner can protect ONE number each.
 * - Protected numbers cannot be blasted.
 * - Only owner can view/remove in admin panel.
 *
 * Storage: protected_numbers/{clean_number_10_digits}
 */
class ProtectionService {

    private val db = FirebaseFirestore.getInstance()

    fun cleanNumber(number: String): String {
        var s = number.trim().replace(" ", "").replace("-", "")
            .replace("(", "").replace(")", "").replace(".", "")
        if (s.startsWith("+91")) s = s.substring(3)
        else if (s.startsWith("+")) s = s.substring(1)
        if (s.startsWith("91") && s.length == 12) s = s.substring(2)
        if (s.startsWith("0") && s.length == 11) s = s.substring(1)
        return s.filter { it.isDigit() }
    }

    // ============================================================
    //  CHECK — used by BlastExecutor before every blast
    // ============================================================
    suspend fun isNumberProtected(number: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val clean = cleanNumber(number)
            if (clean.length < 10) return@withContext false
            val snap = db.collection("protected_numbers").document(clean).get().await()
            snap.exists()
        } catch (e: Exception) {
            Log.e(TAG, "isNumberProtected failed", e)
            false
        }
    }

    // ============================================================
    //  PROTECT — premium / owner self-protect
    // ============================================================
    suspend fun protectNumber(
        uid: String,
        userName: String,
        number: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val clean = cleanNumber(number)
            if (clean.length < 10) {
                return@withContext Result.failure(Exception("Invalid number (10 digits required)"))
            }

            // If user already has a protected number, remove old one
            val userSnap = db.collection(FirebaseConfig.COL_USERS).document(uid).get().await()
            val oldNumber = userSnap.getString("protectedNumber") ?: ""
            if (oldNumber.isNotBlank() && oldNumber != clean) {
                try {
                    db.collection("protected_numbers").document(oldNumber).delete().await()
                } catch (_: Exception) {}
            }

            val now = System.currentTimeMillis() / 1000L
            db.collection("protected_numbers").document(clean).set(
                mapOf(
                    "number" to clean,
                    "uid" to uid,
                    "userName" to userName.take(64),
                    "protectedAt" to now,
                )
            ).await()

            db.collection(FirebaseConfig.COL_USERS).document(uid)
                .update("protectedNumber", clean).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "protectNumber failed", e)
            Result.failure(e)
        }
    }

    // ============================================================
    //  UNPROTECT — user removes own number
    // ============================================================
    suspend fun unprotectNumber(uid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userRef = db.collection(FirebaseConfig.COL_USERS).document(uid)
            val userSnap = userRef.get().await()
            val oldNumber = userSnap.getString("protectedNumber") ?: ""
            if (oldNumber.isNotBlank()) {
                try {
                    db.collection("protected_numbers").document(oldNumber).delete().await()
                } catch (_: Exception) {}
            }
            userRef.update("protectedNumber", "").await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "unprotectNumber failed", e)
            Result.failure(e)
        }
    }

    // ============================================================
    //  LIST ALL — owner only
    // ============================================================
    suspend fun listAllProtected(): List<ProtectedNumber> = withContext(Dispatchers.IO) {
        try {
            db.collection("protected_numbers").get().await().documents.mapNotNull { d ->
                ProtectedNumber(
                    number = d.getString("number") ?: "",
                    uid = d.getString("uid") ?: "",
                    userName = d.getString("userName") ?: "",
                    protectedAt = d.getLong("protectedAt") ?: 0L,
                )
            }.sortedByDescending { it.protectedAt }
        } catch (e: Exception) {
            Log.e(TAG, "listAllProtected failed", e)
            emptyList()
        }
    }

    // ============================================================
    //  ADMIN REMOVE — owner force-removes protection
    // ============================================================
    suspend fun adminRemoveNumber(number: String, uid: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val clean = cleanNumber(number)
            db.collection("protected_numbers").document(clean).delete().await()
            if (uid.isNotBlank()) {
                try {
                    db.collection(FirebaseConfig.COL_USERS).document(uid)
                        .update("protectedNumber", "").await()
                } catch (_: Exception) {}
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "adminRemoveNumber failed", e)
            false
        }
    }

    companion object {
        private const val TAG = "ProtectionService"
    }
}