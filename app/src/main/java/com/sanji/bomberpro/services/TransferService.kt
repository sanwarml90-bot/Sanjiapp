package com.sanji.bomberpro.services

import com.google.firebase.firestore.FirebaseFirestore
import com.sanji.bomberpro.FirebaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class TransferRecipient(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val credits: Int = 0,
    val coins: Int = 0,
)

sealed class TransferResult {
    data class Success(val newOwnBalance: Int, val recipientName: String) : TransferResult()
    data class Failure(val message: String) : TransferResult()
}

class TransferService {

    private val db = FirebaseFirestore.getInstance()
    private val userData = UserDataService()

    /** Find a user by exact telegram-style UID, email, or refer code. Returns null if not found. */
    suspend fun findRecipient(query: String): TransferRecipient? = withContext(Dispatchers.IO) {
        try {
            val q = query.trim()
            if (q.isBlank()) return@withContext null

            // try UID direct
            val uidSnap = db.collection(FirebaseConfig.COL_USERS).document(q).get().await()
            if (uidSnap.exists()) {
                return@withContext parse(uidSnap.id, uidSnap.data ?: emptyMap())
            }

            // try email
            val emailSnap = db.collection(FirebaseConfig.COL_USERS)
                .whereEqualTo("email", q.lowercase())
                .limit(1)
                .get().await()
            if (!emailSnap.isEmpty) {
                val d = emailSnap.documents.first()
                return@withContext parse(d.id, d.data ?: emptyMap())
            }

            // try refer code
            val codeSnap = db.collection(FirebaseConfig.COL_USERS)
                .whereEqualTo("referCode", q.uppercase())
                .limit(1)
                .get().await()
            if (!codeSnap.isEmpty) {
                val d = codeSnap.documents.first()
                return@withContext parse(d.id, d.data ?: emptyMap())
            }

            null
        } catch (_: Exception) {
            null
        }
    }

    private fun parse(uid: String, map: Map<String, Any?>): TransferRecipient {
        return TransferRecipient(
            uid = uid,
            displayName = map["displayName"] as? String ?: "User",
            email = map["email"] as? String ?: "",
            credits = (map["credits"] as? Number)?.toInt() ?: 0,
            coins = (map["coins"] as? Number)?.toInt() ?: 0,
        )
    }

    /** Max transferable = half of current balance (both sides stay funded). */
    fun maxTransferable(currentBalance: Int): Int = currentBalance / 2

    suspend fun transferCredits(
        fromUid: String,
        toUid: String,
        amount: Int,
    ): TransferResult = withContext(Dispatchers.IO) {
        if (amount <= 0) return@withContext TransferResult.Failure("Amount must be > 0")
        if (fromUid == toUid) return@withContext TransferResult.Failure("Cannot transfer to self")

        val current = userData.getCredits(fromUid)
        val max = maxTransferable(current)
        if (amount > max) return@withContext TransferResult.Failure("Max transfer is $max")

        val result = userData.transferCredits(fromUid, toUid, amount)
        if (result.isSuccess) {
            val newBal = userData.getCredits(fromUid)
            val to = findRecipient(toUid)
            TransferResult.Success(newBal, to?.displayName ?: "recipient")
        } else {
            TransferResult.Failure(result.exceptionOrNull()?.message ?: "Transfer failed")
        }
    }

    suspend fun transferCoins(
        fromUid: String,
        toUid: String,
        amount: Int,
    ): TransferResult = withContext(Dispatchers.IO) {
        if (amount <= 0) return@withContext TransferResult.Failure("Amount must be > 0")
        if (fromUid == toUid) return@withContext TransferResult.Failure("Cannot transfer to self")

        val current = userData.getCoins(fromUid)
        if (amount > current) return@withContext TransferResult.Failure("Not enough coins")

        val result = userData.transferCoins(fromUid, toUid, amount)
        if (result.isSuccess) {
            val newBal = userData.getCoins(fromUid)
            val to = findRecipient(toUid)
            TransferResult.Success(newBal, to?.displayName ?: "recipient")
        } else {
            TransferResult.Failure(result.exceptionOrNull()?.message ?: "Transfer failed")
        }
    }
}