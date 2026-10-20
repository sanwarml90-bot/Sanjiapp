package com.sanji.bomberpro.data

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.sanji.bomberpro.FirebaseConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthManager(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val currentUser: FirebaseUser? get() = auth.currentUser

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user == null) {
                _authState.value = AuthState.LoggedOut
            } else {
                scope.launch { loadProfileAndRole(user) }
            }
        }
    }

    // ============================================================
    //  GOOGLE (kept for later — when OAuth consent is set up)
    // ============================================================
    suspend fun signInWithGoogleIdToken(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user
                ?: return Result.failure(Exception("No user returned from Firebase"))
            ensureUserDoc(user)
            loadProfileAndRole(user)
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Google sign-in failed", e)
            _authState.value = AuthState.Error("Sign-in failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ============================================================
    //  EMAIL + PASSWORD — sign-in, else auto sign-up
    // ============================================================
    suspend fun signInOrSignUpWithEmail(
        email: String,
        password: String,
    ): Result<FirebaseUser> {
        val cleanEmail = email.trim().lowercase()
        return try {
            // Try sign-in first
            var user: FirebaseUser? = null
            try {
                val result = auth.signInWithEmailAndPassword(cleanEmail, password).await()
                user = result.user
            } catch (signInError: Exception) {
                Log.w(TAG, "Sign-in failed, trying sign-up: ${signInError.message}")
                // Sign-in failed → try sign-up (new user)
                try {
                    val result = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
                    user = result.user
                } catch (signUpError: Exception) {
                    Log.e(TAG, "Sign-up also failed", signUpError)
                    // Both failed → return the sign-up error (usually EMAIL_IN_USE = wrong password)
                    throw signUpError
                }
            }

            val finalUser = user ?: return Result.failure(Exception("No user returned"))
            ensureUserDoc(finalUser)
            loadProfileAndRole(finalUser)
            Result.success(finalUser)
        } catch (e: Exception) {
            Log.e(TAG, "Email auth failed", e)
            val friendly = when {
                e.message?.contains("password is invalid", true) == true ||
                        e.message?.contains("INVALID_LOGIN_CREDENTIALS", true) == true ->
                    "Wrong password"
                e.message?.contains("already in use", true) == true ||
                        e.message?.contains("EMAIL_EXISTS", true) == true ->
                    "Account exists — wrong password"
                e.message?.contains("badly formatted", true) == true ->
                    "Invalid email format"
                e.message?.contains("network", true) == true ->
                    "Network error — try again"
                else -> e.message ?: "Login failed"
            }
            _authState.value = AuthState.Error(friendly, e)
            Result.failure(Exception(friendly))
        }
    }

    // ============================================================
    //  USER DOC CREATION (auto-owner for owner email)
    // ============================================================
    private suspend fun ensureUserDoc(user: FirebaseUser) {
        try {
            val uid = user.uid
            val userRef = db.collection(FirebaseConfig.COL_USERS).document(uid)
            val snapshot = userRef.get().await()

            if (!snapshot.exists()) {
                val isOwner = user.email?.equals(FirebaseConfig.OWNER_EMAIL, ignoreCase = true) == true
                val role = if (isOwner) "owner" else "user"
                val referCode = generateReferCode(uid)

                userRef.set(
                    mapOf(
                        "uid" to uid,
                        "email" to (user.email ?: ""),
                        "displayName" to (user.displayName ?: (user.email?.substringBefore("@") ?: "")),
                        "photoUrl" to (user.photoUrl?.toString() ?: ""),
                        "role" to role,
                        "credits" to 0,
                        "coins" to 0,
                        "subscription" to mapOf(
                            "plan" to "free",
                            "expiresAt" to 0L,
                            "startedAt" to 0L,
                        ),
                        "totalBlasts" to 0,
                        "totalSms" to 0,
                        "totalOtps" to 0,
                        "dailyBlastsToday" to 0,
                        "lastBlastDate" to 0L,
                        "referCode" to referCode,
                        "referredBy" to null,
                        "banned" to false,
                        "banReason" to "",
                        "termsAccepted" to false,
                        "termsAcceptedAt" to 0L,
                        "createdAt" to nowSec(),
                        "lastActive" to nowSec(),
                    )
                ).await()

                if (isOwner) {
                    try {
                        db.collection(FirebaseConfig.COL_ROLES)
                            .document(hashEmail(FirebaseConfig.OWNER_EMAIL))
                            .set(
                                mapOf(
                                    "email" to FirebaseConfig.OWNER_EMAIL.lowercase(),
                                    "role" to "owner",
                                    "addedBy" to null,
                                    "addedAt" to nowSec(),
                                    "active" to true,
                                )
                            ).await()
                    } catch (e: Exception) {
                        Log.w(TAG, "Owner role doc failed", e)
                    }
                }
            } else {
                val updates = mutableMapOf<String, Any>("lastActive" to nowSec())
                user.displayName?.let { if (it.isNotBlank()) updates["displayName"] = it }
                user.photoUrl?.let { updates["photoUrl"] = it.toString() }
                try {
                    userRef.update(updates).await()
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "ensureUserDoc failed", e)
            // Don't fail the whole login because of this
        }
    }

    // ============================================================
    //  LOAD PROFILE + ROLE (defensive — never blocks login)
    // ============================================================
    private suspend fun loadProfileAndRole(user: FirebaseUser) {
        try {
            val email = user.email ?: ""
            var role = UserRole.USER

            // Role check
            try {
                if (email.equals(FirebaseConfig.OWNER_EMAIL, ignoreCase = true)) {
                    role = UserRole.OWNER
                } else if (email.isNotBlank()) {
                    val roleDoc = db.collection(FirebaseConfig.COL_ROLES)
                        .document(hashEmail(email))
                        .get().await()
                    if (roleDoc.exists() && (roleDoc.getBoolean("active") ?: true)) {
                        role = UserRole.fromString(roleDoc.getString("role"))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Role load failed, defaulting to USER", e)
            }

            // Profile load (never blocks)
            var profile: UserProfile? = null
            try {
                val profileDoc = db.collection(FirebaseConfig.COL_USERS)
                    .document(user.uid)
                    .get().await()

                if (profileDoc.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val sub = profileDoc.get("subscription") as? Map<String, Any?> ?: emptyMap()
                    profile = UserProfile(
                        uid = user.uid,
                        email = profileDoc.getString("email") ?: email,
                        displayName = profileDoc.getString("displayName")
                            ?: user.displayName
                            ?: (user.email?.substringBefore("@") ?: "User"),
                        photoUrl = profileDoc.getString("photoUrl"),
                        role = profileDoc.getString("role") ?: "user",
                        credits = (profileDoc.getLong("credits") ?: 0L).toInt(),
                        coins = (profileDoc.getLong("coins") ?: 0L).toInt(),
                        subscriptionPlan = (sub["plan"] as? String) ?: "free",
                        subscriptionExpiresAt = (sub["expiresAt"] as? Number)?.toLong() ?: 0L,
                        totalBlasts = (profileDoc.getLong("totalBlasts") ?: 0L).toInt(),
                        totalSms = (profileDoc.getLong("totalSms") ?: 0L).toInt(),
                        totalOtps = (profileDoc.getLong("totalOtps") ?: 0L).toInt(),
                        referCode = profileDoc.getString("referCode") ?: "",
                        referredBy = profileDoc.getString("referredBy"),
                        banned = profileDoc.getBoolean("banned") ?: false,
                        banReason = profileDoc.getString("banReason") ?: "",
                        termsAccepted = profileDoc.getBoolean("termsAccepted") ?: false,
                        termsAcceptedAt = profileDoc.getLong("termsAcceptedAt") ?: 0L,
                        createdAt = profileDoc.getLong("createdAt") ?: 0L,
                        lastActive = profileDoc.getLong("lastActive") ?: 0L,
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Profile load failed, proceeding with null profile", e)
            }

            // ALWAYS set LoggedIn — even if profile is null
            _authState.value = AuthState.LoggedIn(user, role, profile)
        } catch (e: Exception) {
            Log.e(TAG, "loadProfileAndRole crashed", e)
            // Even on total failure, still set LoggedIn (user is authenticated)
            _authState.value = AuthState.LoggedIn(user, UserRole.USER, null)
        }
    }

    fun signOut() {
        auth.signOut()
        _authState.value = AuthState.LoggedOut
    }

    private fun generateReferCode(uid: String): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        var seed = uid.hashCode().toLong() and 0x7FFFFFFF
        val sb = StringBuilder("SJ")
        repeat(8) {
            seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
            sb.append(alphabet[(seed % alphabet.length).toInt()])
        }
        return sb.toString()
    }

    private fun hashEmail(email: String): String =
        email.lowercase().trim().replace("@", "_at_").replace(".", "_dot_")

    private fun nowSec(): Long = System.currentTimeMillis() / 1000L

    companion object {
        private const val TAG = "AuthManager"
    }
}