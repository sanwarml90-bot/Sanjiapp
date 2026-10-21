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

    private var cachedOwnerEmail: String? = null

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

    private suspend fun getOwnerEmail(): String {
        cachedOwnerEmail?.let { return it }
        try {
            val doc = db.collection(FirebaseConfig.COL_SETTINGS)
                .document(FirebaseConfig.DOC_GLOBAL_SETTINGS)
                .get().await()
            val email = doc.getString("ownerEmail")
            if (!email.isNullOrBlank()) {
                val clean = email.lowercase()
                cachedOwnerEmail = clean
                return clean
            }
        } catch (_: Exception) {}
        cachedOwnerEmail = FirebaseConfig.OWNER_EMAIL.lowercase()
        return cachedOwnerEmail!!
    }

    fun invalidateOwnerCache() {
        cachedOwnerEmail = null
    }

    // ============================================================
    //  GOOGLE SIGN-IN
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
    //  EMAIL + PASSWORD
    // ============================================================
    suspend fun signInOrSignUpWithEmail(email: String, password: String): Result<FirebaseUser> {
        val cleanEmail = email.trim().lowercase()
        return try {
            var user: FirebaseUser? = null
            try {
                val result = auth.signInWithEmailAndPassword(cleanEmail, password).await()
                user = result.user
            } catch (signInError: Exception) {
                Log.w(TAG, "Sign-in failed, trying sign-up: ${signInError.message}")
                try {
                    val result = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
                    user = result.user
                } catch (signUpError: Exception) {
                    Log.e(TAG, "Sign-up also failed", signUpError)
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
    //  USER DOC CREATION
    // ============================================================
    private suspend fun ensureUserDoc(user: FirebaseUser) {
        try {
            val uid = user.uid
            val userRef = db.collection(FirebaseConfig.COL_USERS).document(uid)
            val snapshot = userRef.get().await()

            if (!snapshot.exists()) {
                val ownerEmail = getOwnerEmail()
                val isOwner = user.email?.equals(ownerEmail, ignoreCase = true) == true
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
                            .document(hashEmail(ownerEmail))
                            .set(
                                mapOf(
                                    "email" to ownerEmail,
                                    "role" to "owner",
                                    "addedBy" to null,
                                    "addedAt" to nowSec(),
                                    "active" to true,
                                ),
                                com.google.firebase.firestore.SetOptions.merge(),
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
        }
    }

    // ============================================================
    //  LOAD PROFILE + ROLE
    // ============================================================
    private suspend fun loadProfileAndRole(user: FirebaseUser) {
        try {
            val email = user.email ?: ""
            var role = UserRole.USER

            val ownerEmail = getOwnerEmail()
            try {
                if (email.equals(ownerEmail, ignoreCase = true)) {
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

                    // Sync role if changed (transfer ownership case)
                    val storedRole = profileDoc.getString("role") ?: "user"
                    val computedRole = when (role) {
                        UserRole.OWNER -> "owner"
                        UserRole.SUPER_ADMIN -> "super_admin"
                        UserRole.ADMIN -> "admin"
                        UserRole.USER -> "user"
                    }
                    if (storedRole != computedRole) {
                        try {
                            db.collection(FirebaseConfig.COL_USERS).document(user.uid)
                                .update("role", computedRole).await()
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Profile load failed, proceeding with null profile", e)
            }

            _authState.value = AuthState.LoggedIn(user, role, profile)
        } catch (e: Exception) {
            Log.e(TAG, "loadProfileAndRole crashed", e)
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