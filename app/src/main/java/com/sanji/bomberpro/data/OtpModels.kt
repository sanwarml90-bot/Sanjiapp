package com.sanji.bomberpro.data

data class OtpExtracted(
    val code: String,
    val score: Int,
    val snippet: String,
)

data class OtpMessage(
    val msgId: String,
    val hash: String,
    val sender: String,
    val body: String,
    val ts: Long,
    val otps: List<String>,
    val hasOtp: Boolean,
    val score: Int,
)

data class OtpFetchResult(
    val otps: List<OtpMessage>,
    val allMessages: List<OtpMessage>,
    val scannedAt: Long,
)

data class OtpLiveState(
    val startedAt: Long,
    val foundOtps: List<OtpMessage> = emptyList(),
    val coinsSpent: Int = 0,
    val cancelled: Boolean = false,
)