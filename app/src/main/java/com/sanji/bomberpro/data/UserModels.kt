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
    val oneDeviceOneAccount: Boolean = true,
    val specialMsgEnabled: Boolean = false,
    val specialMsgVersion: Int = 0,
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

// ============================================================
//  DEVICE LOCK
// ============================================================
data class DeviceLock(
    val fingerprint: String = "",
    val uid: String = "",
    val email: String = "",
    val userName: String = "",
    val deviceModel: String = "",
    val deviceBrand: String = "",
    val androidVersion: String = "",
    val sdkInt: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

// ============================================================
//  SPECIAL MSG
// ============================================================
data class SpecialMessage(
    val id: String = "",
    val version: Int = 0,
    val title: String = "",
    val body: String = "",
    val bodyHtml: String = "",
    val isHtml: Boolean = false,
    val enabled: Boolean = false,
    val publishedBy: String = "",
    val publishedAt: Long = 0L,
    val buttonText: String = "",
    val buttonUrl: String = "",
)

// ============================================================
//  DELETED ACCOUNT
// ============================================================
data class DeletedAccount(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val username: String = "",
    val role: String = "user",
    val credits: Int = 0,
    val coins: Int = 0,
    val totalBlasts: Int = 0,
    val totalSms: Int = 0,
    val totalOtps: Int = 0,
    val createdAt: Long = 0L,
    val lastActive: Long = 0L,
    val deletedAt: Long = 0L,
    val deletedBy: String = "",
    val reason: String = "",
    val deviceFingerprint: String = "",
    val snapshot: Map<String, Any?> = emptyMap(),
)

// ============================================================
//  ACCOUNT ANALYSIS
// ============================================================
data class AnalyzedUser(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val username: String = "",
    val role: String = "user",
    val credits: Int = 0,
    val coins: Int = 0,
    val totalBlasts: Int = 0,
    val totalSms: Int = 0,
    val totalOtps: Int = 0,
    val createdAt: Long = 0L,
    val lastActive: Long = 0L,
    val banned: Boolean = false,
    val deviceFingerprint: String = "",
    val daysInactive: Int = 0,
    val daysOld: Int = 0,
)

data class DuplicateGroup(
    val fingerprint: String = "",
    val users: List<AnalyzedUser> = emptyList(),
)

data class AccountAnalysis(
    val totalUsers: Int = 0,
    val inactive: List<AnalyzedUser> = emptyList(),
    val dead: List<AnalyzedUser> = emptyList(),
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val computedAt: Long = 0L,
)

// ============================================================
//  PROTECTED NUMBER
// ============================================================
/**
 * Stored at: protected_numbers/{cleanNumber}
 * Premium users and owner can protect ONE number each.
 * That number cannot be bombed by anyone.
 * Only owner can view/remove from admin panel.
 */
data class ProtectedNumber(
    val number: String = "",
    val uid: String = "",
    val userName: String = "",
    val protectedAt: Long = 0L,
)