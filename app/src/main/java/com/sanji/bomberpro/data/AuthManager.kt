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
                        "displayName" to (user.displayName ?: ""),
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
                }
            } else {
                val updates = mutableMapOf<String, Any>("lastActive" to nowSec())
                user.displayName?.let { if (it.isNotBlank()) updates["displayName"] = it }
                user.photoUrl?.let { updates["photoUrl"] = it.toString() }
                userRef.update(updates).await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "ensureUserDoc failed", e)
        }
    }

    private suspend fun loadProfileAndRole(user: FirebaseUser) {
        try {
            val email = user.email ?: ""
            var role = UserRole.USER

            if (email.isNotBlank()) {
                if (email.equals(FirebaseConfig.OWNER_EMAIL, ignoreCase = true)) {
                    role = UserRole.OWNER
                } else {
                    val roleDoc = db.collection(FirebaseConfig.COL_ROLES)
                        .document(hashEmail(email))
                        .get().await()

                    if (roleDoc.exists() && (roleDoc.getBoolean("active") ?: true)) {
                        role = UserRole.fromString(roleDoc.getString("role"))
                    }
                }
            }

            val profileDoc = db.collection(FirebaseConfig.COL_USERS)
                .document(user.uid)
                .get().await()

            val profile = if (profileDoc.exists()) {
                @Suppress("UNCHECKED_CAST")
                val sub = profileDoc.get("subscription") as? Map