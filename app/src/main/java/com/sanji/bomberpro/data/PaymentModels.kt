package com.sanji.bomberpro.data

data class PaymentRequest(
    val id: String = "",
    val uid: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val type: String = "credits",
    val packId: String = "",
    val packName: String = "",
    val creditsAmount: Int = 0,
    val plan: String = "",
    val amount: Int = 0,
    val utr: String = "",
    val description: String = "",
    val status: String = "pending",
    val telegramMsgId: Long = 0L,
    val createdAt: Long = 0L,
    val reviewedAt: Long = 0L,
    val reviewedBy: String = "",
    val rejectionReason: String = "",
)