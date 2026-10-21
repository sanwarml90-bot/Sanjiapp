package com.sanji.bomberpro.data

data class CreditTransaction(
    val id: String = "",
    val uid: String = "",
    val type: String = "",
    val delta: Int = 0,
    val reason: String = "",
    val ts: Long = 0L,
)

data class ActivityEntry(
    val id: String = "",
    val uid: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val action: String = "",
    val targetMasked: String = "",
    val targetFull: String = "",
    val details: Map<String, Any> = emptyMap(),
    val ts: Long = 0L,
)

data class BlastRecord(
    val id: String = "",
    val uid: String = "",
    val type: String = "single",
    val targetFull: String = "",
    val targetMasked: String = "",
    val numbersCount: Int = 0,
    val message: String = "",
    val requested: Int = 0,
    val sent: Int = 0,
    val failed: Int = 0,
    val durationSec: Long = 0L,
    val speed: String = "",
    val devicePool: List<String> = emptyList(),
    val status: String = "completed",
    val ts: Long = 0L,
)

data class OtpRecord(
    val id: String = "",
    val uid: String = "",
    val otp: String = "",
    val sender: String = "",
    val deviceName: String = "",
    val deviceShort: String = "",
    val devicePhone: String = "",
    val firebaseLabel: String = "",
    val mode: String = "",
    val cost: Int = 0,
    val refunded: Boolean = false,
    val ts: Long = 0L,
)

data class GlobalSettings(
    val otpCost: Int = 1,
    val creditCostPerSms: Int = 1,
    val bulkCreditCostPerNumber: Int = 1,
    val dailyBonusCredits: Int = 2,
    val refCredits: Int = 3,
    val refCoins: Int = 1,
    val latestVersion: String = "1.0.0",
    val minVersion: String = "1.0.0",
    val apkUrl: String = "",
    val changelog: String = "",
    val forceUpdate: Boolean = false,
    val upiId: String = "",
    val upiName: String = "",
    val maintenance: Boolean = false,
    val maintenanceMsg: String = "",
    val termsVersion: Int = 1,
    val termsRequired: Boolean = true,
    val forceJoinEnabled: Boolean = false,
    val otpFreshWindowSec: Long = 900L,
)

data class ReferralResult(
    val referrerUid: String,
    val creditsAwarded: Int,
    val coinsAwarded: Int,
)

// ============================================================
//  FORCE JOIN
// ============================================================
data class ForceJoinChannel(
    val id: String = "",
    val title: String = "",
    val link: String = "",
    val required: Boolean = true,
)

data class ForceJoinState(
    val enabled: Boolean = false,
    val channels: List<ForceJoinChannel> = emptyList(),
    val bypass: List<String> = emptyList(),
)

// ============================================================
//  TERMS
// ============================================================
data class TermsContent(
    val version: Int = 1,
    val text: String = "",
)