package com.sanji.bomberpro.data

import com.google.firebase.auth.FirebaseUser

sealed class AuthState {
    data object Initializing : AuthState()
    data object LoggedOut : AuthState()
    data class LoggedIn(
        val user: FirebaseUser,
        val role: UserRole,
        val profile: UserProfile?,
    ) : AuthState()
    data class Error(val message: String, val cause: Throwable? = null) : AuthState()
}

enum class UserRole(val priority: Int) {
    USER(0),
    ADMIN(10),
    SUPER_ADMIN(20),
    OWNER(30);

    val canAccessAdminPanel: Boolean
        get() = priority >= ADMIN.priority

    val canManageFirebases: Boolean
        get() = priority >= SUPER_ADMIN.priority

    val canManageRoles: Boolean
        get() = this == OWNER

    val canManageSettings: Boolean
        get() = priority >= SUPER_ADMIN.priority

    val canEditOwner: Boolean
        get() = this == OWNER

    companion object {
        fun fromString(s: String?): UserRole = when (s?.lowercase()?.trim()) {
            "owner" -> OWNER
            "super_admin" -> SUPER_ADMIN
            "admin" -> ADMIN
            else -> USER
        }
    }
}

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val role: String = "user",
    val credits: Int = 0,
    val coins: Int = 0,
    val manualAddedCredits: Int = 0,
    val manualAddedCoins: Int = 0,
    val subscriptionPlan: String = "free",
    val subscriptionExpiresAt: Long = 0L,
    val totalBlasts: Int = 0,
    val totalSms: Int = 0,
    val totalOtps: Int = 0,
    val referCode: String = "",
    val referredBy: String? = null,
    val banned: Boolean = false,
    val banReason: String = "",
    val termsAccepted: Boolean = false,
    val termsAcceptedAt: Long = 0L,
    val termsVersion: Int = 0,
    val premium: Boolean = false,
    val createdAt: Long = 0L,
    val lastActive: Long = 0L,
) {
    /**
     * True if user needs to (re)accept terms.
     * Compares stored version vs current global terms version.
     */
    fun needsTermsAcceptance(currentTermsVersion: Int, termsRequired: Boolean): Boolean {
        if (!termsRequired) return false
        if (!termsAccepted) return true
        return termsVersion < currentTermsVersion
    }
}