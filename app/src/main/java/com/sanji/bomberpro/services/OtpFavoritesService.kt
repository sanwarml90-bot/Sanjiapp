package com.sanji.bomberpro.services

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sanji.bomberpro.FirebaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class OtpFavorite(
    val id: String = "",
    val otp: String = "",
    val sender: String = "",
    val deviceName: String = "",
    val savedAt: Long = 0L,
)

class OtpFavoritesService {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    private fun favsCol(uid: String) =
        db.collection(FirebaseConfig.COL_USERS).document(uid).collection("otp_favorites")

    suspend fun listFavorites(uid: String): List<OtpFavorite> = withContext(Dispatchers.IO) {
        try {
            favsCol(uid).orderBy("savedAt", Query.Direction.DESCENDING).get().await()
                .documents.map { d ->
                    OtpFavorite(
                        id = d.id,
                        otp = d.getString("otp") ?: "",
                        sender = d.getString("sender") ?: "",
                        deviceName = d.getString("deviceName") ?: "",
                        savedAt = d.getLong("savedAt") ?: 0L,
                    )
                }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun addFavorite(uid: String, otp: String, sender: String, deviceName: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                favsCol(uid).add(
                    hashMapOf(
                        "otp" to otp,
                        "sender" to sender,
                        "deviceName" to deviceName,
                        "savedAt" to System.currentTimeMillis() / 1000L,
                    )
                ).await()
                true
            } catch (_: Exception) { false }
        }

    suspend fun removeFavorite(uid: String, favId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                favsCol(uid).document(favId).delete().await()
                true
            } catch (_: Exception) { false }
        }

    suspend fun getWatchKeyword(uid: String): String = withContext(Dispatchers.IO) {
        try {
            val snap = db.collection(FirebaseConfig.COL_USERS).document(uid).get().await()
            snap.getString("otpWatchKeyword") ?: ""
        } catch (_: Exception) { "" }
    }

    suspend fun setWatchKeyword(uid: String, keyword: String): Boolean = withContext(Dispatchers.IO) {
        try {
            db.collection(FirebaseConfig.COL_USERS).document(uid)
                .update("otpWatchKeyword", keyword.trim().lowercase()).await()
            true
        } catch (_: Exception) { false }
    }

    fun matchesKeyword(keyword: String, body: String): Boolean {
        if (keyword.isBlank()) return false
        return body.lowercase().contains(keyword.lowercase())
    }
}