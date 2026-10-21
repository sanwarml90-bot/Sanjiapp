package com.sanji.bomberpro.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class GiftResult {
    data class Success(val newOwnBalance: Int, val recipientName: String, val amount: Int) : GiftResult()
    data class Failure(val message: String) : GiftResult()
}

class GiftService {

    private val userData = UserDataService()
    private val transferService = TransferService()

    fun maxGiftable(currentBalance: Int): Int = currentBalance

    suspend fun findRecipient(query: String): TransferRecipient? =
        transferService.findRecipient(query)

    suspend fun giftCoins(
        fromUid: String,
        toUid: String,
        amount: Int,
        note: String = "",
    ): GiftResult = withContext(Dispatchers.IO) {
        if (amount <= 0) return@withContext GiftResult.Failure("Amount must be > 0")
        if (fromUid == toUid) return@withContext GiftResult.Failure("Cannot gift to self")

        val current = userData.getCoins(fromUid)
        if (amount > current) return@withContext GiftResult.Failure("Not enough coins")

        val result = userData.giftCoins(fromUid, toUid, amount, note)
        if (result.isSuccess) {
            val newBal = userData.getCoins(fromUid)
            val to = transferService.findRecipient(toUid)
            GiftResult.Success(newBal, to?.displayName ?: "friend", amount)
        } else {
            GiftResult.Failure(result.exceptionOrNull()?.message ?: "Gift failed")
        }
    }
}