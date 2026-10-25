package com.sanji.bomberpro.services

import com.google.firebase.firestore.FirebaseFirestore
import com.sanji.bomberpro.FirebaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class Referrer(
    val uid: String,
    val name: String,
    val count: Int,
)

class ReferralLeaderboardService {

    private val db = FirebaseFirestore.getInstance()

    suspend fun getTopReferrers(limit: Int = 10): List<Referrer> = withContext(Dispatchers.IO) {
        try {
            val usersSnap = db.collection(FirebaseConfig.COL_USERS)
                .limit(5000)
                .get().await()
            val counts = mutableMapOf<String, Int>()
            for (d in usersSnap.documents) {
                val referrer = d.getString("referredBy") ?: continue
                if (referrer.isBlank()) continue
                counts[referrer] = (counts[referrer] ?: 0) + 1
            }
            val sorted = counts.entries.sortedByDescending { it.value }.take(limit)
            sorted.mapNotNull { (uid, count) ->
                val doc = usersSnap.documents.firstOrNull { it.id == uid } ?: return@mapNotNull null
                Referrer(
                    uid = uid,
                    name = doc.getString("displayName") ?: doc.getString("username") ?: "User",
                    count = count,
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}