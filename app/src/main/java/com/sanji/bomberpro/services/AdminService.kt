package com.sanji.bomberpro.services

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.sanji.bomberpro.FirebaseConfig
import com.sanji.bomberpro.data.FirebaseNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class AdminUser(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val role: String = "user",
    val credits: Int = 0,
    val coins: Int = 0,
    val manualAddedCredits: Int = 0,
    val manualAddedCoins: Int = 0,
    val totalBlasts: Int = 0,
    val totalSms: Int = 0,
    val totalOtps: Int = 0,
    val referCode: String = "",
    val referredBy: String? = null,
    val banned: Boolean = false,
    val banReason: String = "",
    val premium: Boolean = false,
    val subscriptionPlan: String = "free",
    val subscriptionExpiresAt: Long = 0L,
    val maxSendLimit: Int? = null,
    val maxOtpLimit: Int? = null,
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val lastActive: Long = 0L,
    val termsAccepted: Boolean = false,
)

data class RoleEntry(
    val email: String,
    val role: String,
    val addedBy: String,
    val addedAt: Long,
)

data class RolesList(
    val owner: String,
    val superAdmins: List<RoleEntry>,
    val admins: List<RoleEntry>,
)

class AdminService {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    // ============================================================
    //  FIREBASES
    // ============================================================
    suspend fun listFirebases(): List<FirebaseNode> = withContext(Dispatchers.IO) {
        try {
            db.collection(FirebaseConfig.COL_FIREBASES)
                .orderBy("addedAt", Query.Direction.ASCENDING)
                .get().await()
                .documents.mapNotNull { d ->
                    val url = d.getString("url") ?: return@mapNotNull null
                    FirebaseNode(
                        id = d.id,
                        url = url,
                        label = d.getString("label") ?: "Firebase",
                        enabled = d.getBoolean("enabled") ?: true,
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addFirebase(url: String, label: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = url.trim().trimEnd('/')
            if (!isValidFirebaseUrl(cleanUrl)) {
                return@withContext Result.failure(Exception("Invalid Firebase URL"))
            }
            val existing = db.collection(FirebaseConfig.COL_FIREBASES)
                .whereEqualTo("url", cleanUrl)
                .limit(1).get().await()
            if (!existing.isEmpty) {
                return@withContext Result.failure(Exception("URL already exists"))
            }
            val ref = db.collection(FirebaseConfig.COL_FIREBASES).document()
            ref.set(
                mapOf(
                    "id" to ref.id,
                    "url" to cleanUrl,
                    "label" to label.take(30).ifBlank { "Firebase" },
                    "enabled" to true,
                    "addedAt" to System.currentTimeMillis() / 1000L,
                )
            ).await()
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFirebase(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            db.collection(FirebaseConfig.COL_FIREBASES).document(id).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun toggleFirebase(id: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            db.collection(FirebaseConfig.COL_FIREBASES).document(id)
                .update("enabled", enabled).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isValidFirebaseUrl(url: String): Boolean {
        val l = url.trim().lowercase()
        if (!l.startsWith("http")) return false
        return l.contains("firebaseio.com") ||
                l.contains("firebasedatabase.app") ||
                l.contains("firebase.com")
    }

    // ============================================================
    //  USERS
    // ============================================================
    suspend fun listUsers(limit: Long = 200): List<AdminUser> = withContext(Dispatchers.IO) {
        try {
            db.collection(FirebaseConfig.COL_USERS)
                .orderBy("lastActive", Query.Direction.DESCENDING)
                .limit(limit)
                .get().await()
                .documents.mapNotNull { parseUser(it.id, it.data ?: emptyMap()) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchUsers(query: String, limit: Long = 200): List<AdminUser> {
        val all = listUsers(limit)
        if (query.isBlank()) return all
        val q = query.trim().lowercase()
        return all.filter {
            it.uid.lowercase().contains(q) ||
                    it.email.lowercase().contains(q) ||
                    it.displayName.lowercase().contains(q)
        }
    }

    suspend fun getUserDetail(uid: String): AdminUser? = withContext(Dispatchers.IO) {
        try {
            val snap = db.collection(FirebaseConfig.COL_USERS).document(uid).get().await()
            if (!snap.exists()) return@withContext null
            parseUser(snap.id, snap.data ?: emptyMap())
        } catch (e: Exception) {
            null
        }
    }

    private fun parseUser(id: String, map: Map<String, Any?>): AdminUser? {
        if (map.isEmpty()) return null
        val sub = map["subscription"] as? Map<*, *>
        return AdminUser(
            uid = id,
            email = map["email"] as? String ?: "",
            displayName = map["displayName"] as? String ?: "User",
            photoUrl = map["photoUrl"] as? String,
            role = map["role"] as? String ?: "user",
            credits = (map["credits"] as? Number)?.toInt() ?: 0,
            coins = (map["coins"] as? Number)?.toInt() ?: 0,
            manualAddedCredits = (map["manualAddedCredits"] as? Number)?.toInt() ?: 0,
            manualAddedCoins = (map["manualAddedCoins"] as? Number)?.toInt() ?: 0,
            totalBlasts = (map["totalBlasts"] as? Number)?.toInt() ?: 0,
            totalSms = (map["totalSms"] as? Number)?.toInt() ?: 0,
            totalOtps = (map["totalOtps"] as? Number)?.toInt() ?: 0,
            referCode = map["referCode"] as? String ?: "",
            referredBy = map["referredBy"] as? String,
            banned = map["banned"] as? Boolean ?: false,
            banReason = map["banReason"] as? String ?: "",
            premium = map["premium"] as? Boolean ?: false,
            subscriptionPlan = (sub?.get("plan") as? String) ?: "free",
            subscriptionExpiresAt = (sub?.get("expiresAt") as? Number)?.toLong() ?: 0L,
            maxSendLimit = (map["maxSendLimit"] as? Number)?.toInt(),
            maxOtpLimit = (map["maxOtpLimit"] as? Number)?.toInt(),
            notes = map["notes"] as? String ?: "",
            tags = (map["tags"] as? List<*>)?.map { it.toString() } ?: emptyList(),
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
            lastActive = (map["lastActive"] as? Number)?.toLong() ?: 0L,
            termsAccepted = map["termsAccepted"] as? Boolean ?: false,
        )
    }

    // ============================================================
    //  CREDITS / COINS ADJUST
    // ============================================================
    suspend fun addCreditsToUser(uid: String, amount: Int, adminUid: String, reason: String = "admin-add"): Boolean =
        withContext(Dispatchers.IO) {
            if (amount <= 0) return@withContext false
            try {
                db.collection(FirebaseConfig.COL_USERS).document(uid)
                    .update(
                        "credits", FieldValue.increment(amount.toLong()),
                        "manualAddedCredits", FieldValue.increment(amount.toLong()),
                    ).await()
                logTransaction(uid, "credit_add_manual", amount, reason)
                logAudit(adminUid, "admin_credit_add", uid, amount, reason)
                true
            } catch (e: Exception) {
                false
            }
        }

    suspend fun deductCreditsFromUser(uid: String, amount: Int, adminUid: String): Boolean =
        withContext(Dispatchers.IO) {
            if (amount <= 0) return@withContext false
            val ref = db.collection(FirebaseConfig.COL_USERS).document(uid)
            try {
                val snap = ref.get().await()
                val current = (snap.getLong("credits") ?: 0L).toInt()
                if (current < amount) return@withContext false
                val manual = (snap.getLong("manualAddedCredits") ?: 0L).toInt()
                val cut = minOf(amount, manual)
                ref.update(
                    "credits", (current - amount).toLong(),
                    "manualAddedCredits", (manual - cut).toLong().coerceAtLeast(0L),
                ).await()
                logTransaction(uid, "credit_deduct", -amount, "admin-deduct")
                logAudit(adminUid, "admin_credit_deduct", uid, amount, "")
                true
            } catch (e: Exception) {
                false
            }
        }

    suspend fun addCoinsToUser(uid: String, amount: Int, adminUid: String): Boolean =
        withContext(Dispatchers.IO) {
            if (amount <= 0) return@withContext false
            try {
                db.collection(FirebaseConfig.COL_USERS).document(uid)
                    .update(
                        "coins", FieldValue.increment(amount.toLong()),
                        "manualAddedCoins", FieldValue.increment(amount.toLong()),
                    ).await()
                logTransaction(uid, "coin_add_manual", amount, "admin-add")
                logAudit(adminUid, "admin_coin_add", uid, amount, "")
                true
            } catch (e: Exception) {
                false
            }
        }

    suspend fun deductCoinsFromUser(uid: String, amount: Int, adminUid: String): Boolean =
        withContext(Dispatchers.IO) {
            if (amount <= 0) return@withContext false
            val ref = db.collection(FirebaseConfig.COL_USERS).document(uid)
            try {
                val snap = ref.get().await()
                val current = (snap.getLong("coins") ?: 0L).toInt()
                if (current < amount) return@withContext false
                val manual = (snap.getLong("manualAddedCoins") ?: 0L).toInt()
                val cut = minOf(amount, manual)
                ref.update(
                    "coins", (current - amount).toLong(),
                    "manualAddedCoins", (manual - cut).toLong().coerceAtLeast(0L),
                ).await()
                logTransaction(uid, "coin_deduct", -amount, "admin-deduct")
                logAudit(adminUid, "admin_coin_deduct", uid, amount, "")
                true
            } catch (e: Exception) {
                false
            }
        }

    // ============================================================
    //  BULK ADD / DEDUCT (all users)
    // ============================================================
    suspend fun addCreditsToAll(amount: Int, adminUid: String): Result<Int> = withContext(Dispatchers.IO) {
        if (amount <= 0) return@withContext Result.failure(Exception("Amount must be > 0"))
        try {
            val users = db.collection(FirebaseConfig.COL_USERS).get().await().documents
            var count = 0
            val chunks = users.chunked(400)
            for (chunk in chunks) {
                val batch = db.batch()
                for (u in chunk) {
                    batch.update(
                        u.reference,
                        "credits", FieldValue.increment(amount.toLong()),
                        "manualAddedCredits", FieldValue.increment(amount.toLong()),
                    )
                    count++
                }
                batch.commit().await()
            }
            logAudit(adminUid, "admin_credit_add_all", "", amount, "count=$count")
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deductCreditsFromAll(amount: Int, adminUid: String): Result<Int> = withContext(Dispatchers.IO) {
        if (amount < 0) return@withContext Result.failure(Exception("Amount must be >= 0"))
        try {
            val users = db.collection(FirebaseConfig.COL_USERS).get().await().documents
            var totalDeducted = 0
            var usersAffected = 0
            for (u in users) {
                try {
                    val man = (u.getLong("manualAddedCredits") ?: 0L).toInt()
                    if (man <= 0) continue
                    val cut = minOf(amount, man)
                    if (cut <= 0) continue
                    u.reference.update(
                        "credits", FieldValue.increment(-cut.toLong()),
                        "manualAddedCredits", FieldValue.increment(-cut.toLong()),
                    ).await()
                    totalDeducted += cut
                    usersAffected++
                } catch (_: Exception) {}
            }
            logAudit(adminUid, "admin_credit_deduct_all", "", amount, "got=$totalDeducted from=$usersAffected")
            Result.success(usersAffected)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============================================================
    //  BAN / UNBAN
    // ============================================================
    suspend fun banUser(uid: String, reason: String, adminUid: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_USERS).document(uid)
                    .update(mapOf("banned" to true, "banReason" to reason.take(200))).await()
                logAudit(adminUid, "admin_ban", uid, 0, reason)
                true
            } catch (e: Exception) {
                false
            }
        }

    suspend fun unbanUser(uid: String, adminUid: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_USERS).document(uid)
                    .update(mapOf("banned" to false, "banReason" to "")).await()
                logAudit(adminUid, "admin_unban", uid, 0, "")
                true
            } catch (e: Exception) {
                false
            }
        }

    // ============================================================
    //  LIMITS
    // ============================================================
    suspend fun setSendLimit(uid: String, limit: Int?, adminUid: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val ref = db.collection(FirebaseConfig.COL_USERS).document(uid)
                if (limit == null) ref.update("maxSendLimit", FieldValue.delete()).await()
                else ref.update("maxSendLimit", limit.toLong()).await()
                logAudit(adminUid, "admin_set_send_limit", uid, limit ?: -1, "")
                true
            } catch (e: Exception) {
                false
            }
        }

    suspend fun setOtpLimit(uid: String, limit: Int?, adminUid: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val ref = db.collection(FirebaseConfig.COL_USERS).document(uid)
                if (limit == null) ref.update("maxOtpLimit", FieldValue.delete()).await()
                else ref.update("maxOtpLimit", limit.toLong()).await()
                logAudit(adminUid, "admin_set_otp_limit", uid, limit ?: -1, "")
                true
            } catch (e: Exception) {
                false
            }
        }

    // ============================================================
    //  NOTES / TAGS
    // ============================================================
    suspend fun setNotes(uid: String, notes: String, adminUid: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_USERS).document(uid)
                    .update("notes", notes.take(500)).await()
                logAudit(adminUid, "admin_set_notes", uid, 0, notes.take(80))
                true
            } catch (e: Exception) {
                false
            }
        }

    suspend fun setTags(uid: String, tags: List<String>, adminUid: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_USERS).document(uid)
                    .update("tags", tags.take(10)).await()
                logAudit(adminUid, "admin_set_tags", uid, 0, tags.joinToString(",").take(120))
                true
            } catch (e: Exception) {
                false
            }
        }

    // ============================================================
    //  PREMIUM
    // ============================================================
    suspend fun setPremium(uid: String, premium: Boolean, adminUid: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_USERS).document(uid)
                    .update("premium", premium).await()
                logAudit(adminUid, "admin_premium", uid, if (premium) 1 else 0, "")
                true
            } catch (e: Exception) {
                false
            }
        }

    // ============================================================
    //  SUBSCRIPTION
    // ============================================================
    suspend fun grantSubscription(uid: String, plan: String, days: Int, adminUid: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis() / 1000L
                val expiry = now + days.toLong() * 86400L
                db.collection(FirebaseConfig.COL_USERS).document(uid)
                    .update(
                        mapOf(
                            "subscription.plan" to plan,
                            "subscription.expiresAt" to expiry,
                            "subscription.startedAt" to now,
                        )
                    ).await()
                logAudit(adminUid, "admin_grant_sub", uid, days, plan)
                true
            } catch (e: Exception) {
                false
            }
        }

    suspend fun revokeSubscription(uid: String, adminUid: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_USERS).document(uid)
                    .update(
                        mapOf(
                            "subscription.plan" to "free",
                            "subscription.expiresAt" to 0L,
                            "subscription.startedAt" to 0L,
                        )
                    ).await()
                logAudit(adminUid, "admin_revoke_sub", uid, 0, "")
                true
            } catch (e: Exception) {
                false
            }
        }

    // ============================================================
    //  ROLES
    // ============================================================
    suspend fun getOwnerEmail(): String = withContext(Dispatchers.IO) {
        try {
            val doc = db.collection(FirebaseConfig.COL_SETTINGS)
                .document(FirebaseConfig.DOC_GLOBAL_SETTINGS)
                .get().await()
            val email = doc.getString("ownerEmail")
            if (!email.isNullOrBlank()) email.lowercase() else FirebaseConfig.OWNER_EMAIL.lowercase()
        } catch (e: Exception) {
            FirebaseConfig.OWNER_EMAIL.lowercase()
        }
    }

    suspend fun listRoles(): RolesList = withContext(Dispatchers.IO) {
        val owner = getOwnerEmail()
        val superAdmins = mutableListOf<RoleEntry>()
        val admins = mutableListOf<RoleEntry>()

        try {
            val docs = db.collection(FirebaseConfig.COL_ROLES).get().await()
            for (d in docs.documents) {
                val email = d.getString("email") ?: continue
                val role = d.getString("role") ?: continue
                val active = d.getBoolean("active") ?: true
                if (!active) continue
                if (email.equals(owner, ignoreCase = true)) continue

                val entry = RoleEntry(
                    email = email,
                    role = role,
                    addedBy = d.getString("addedBy") ?: "",
                    addedAt = d.getLong("addedAt") ?: 0L,
                )
                when (role) {
                    "super_admin" -> superAdmins.add(entry)
                    "admin" -> admins.add(entry)
                }
            }
        } catch (_: Exception) {}

        RolesList(
            owner = owner,
            superAdmins = superAdmins.sortedBy { it.email },
            admins = admins.sortedBy { it.email },
        )
    }

    suspend fun addRole(
        email: String,
        role: String,
        addedByEmail: String,
        adminUid: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanEmail = email.trim().lowercase()
            if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
                return@withContext Result.failure(Exception("Invalid email"))
            }
            if (role !in listOf("admin", "super_admin")) {
                return@withContext Result.failure(Exception("Invalid role"))
            }
            if (cleanEmail == getOwnerEmail()) {
                return@withContext Result.failure(Exception("Email is the owner"))
            }

            val hash = hashEmail(cleanEmail)
            val existing = db.collection(FirebaseConfig.COL_ROLES).document(hash).get().await()
            if (existing.exists()) {
                val existingRole = existing.getString("role")
                val active = existing.getBoolean("active") ?: true
                if (active && existingRole != null && existingRole != "user") {
                    return@withContext Result.failure(Exception("Already has role: $existingRole"))
                }
            }

            db.collection(FirebaseConfig.COL_ROLES).document(hash).set(
                mapOf(
                    "email" to cleanEmail,
                    "role" to role,
                    "addedBy" to addedByEmail.lowercase(),
                    "addedAt" to System.currentTimeMillis() / 1000L,
                    "active" to true,
                ),
                SetOptions.merge(),
            ).await()

            try {
                val usersSnap = db.collection(FirebaseConfig.COL_USERS)
                    .whereEqualTo("email", cleanEmail)
                    .limit(1).get().await()
                if (!usersSnap.isEmpty) {
                    usersSnap.documents.first().reference.update("role", role).await()
                }
            } catch (_: Exception) {}

            logAudit(adminUid, "admin_add_role", "", 0, "$cleanEmail=$role")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeRole(
        email: String,
        adminUid: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanEmail = email.trim().lowercase()
            if (cleanEmail == getOwnerEmail()) {
                return@withContext Result.failure(Exception("Cannot remove owner"))
            }
            val hash = hashEmail(cleanEmail)
            db.collection(FirebaseConfig.COL_ROLES).document(hash)
                .update("active", false).await()

            try {
                val usersSnap = db.collection(FirebaseConfig.COL_USERS)
                    .whereEqualTo("email", cleanEmail)
                    .limit(1).get().await()
                if (!usersSnap.isEmpty) {
                    usersSnap.documents.first().reference.update("role", "user").await()
                }
            } catch (_: Exception) {}

            logAudit(adminUid, "admin_remove_role", "", 0, cleanEmail)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun transferOwnership(
        newOwnerEmail: String,
        currentOwnerUid: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanNew = newOwnerEmail.trim().lowercase()
            if (cleanNew.isBlank() || !cleanNew.contains("@")) {
                return@withContext Result.failure(Exception("Invalid email"))
            }
            val currentOwner = getOwnerEmail()
            if (cleanNew == currentOwner) {
                return@withContext Result.failure(Exception("Already owner"))
            }

            db.collection(FirebaseConfig.COL_SETTINGS)
                .document(FirebaseConfig.DOC_GLOBAL_SETTINGS)
                .set(
                    mapOf("ownerEmail" to cleanNew),
                    SetOptions.merge(),
                ).await()

            val oldHash = hashEmail(currentOwner)
            db.collection(FirebaseConfig.COL_ROLES).document(oldHash).set(
                mapOf(
                    "email" to currentOwner,
                    "role" to "super_admin",
                    "addedBy" to cleanNew,
                    "addedAt" to System.currentTimeMillis() / 1000L,
                    "active" to true,
                ),
                SetOptions.merge(),
            ).await()

            val newHash = hashEmail(cleanNew)
            db.collection(FirebaseConfig.COL_ROLES).document(newHash).set(
                mapOf(
                    "email" to cleanNew,
                    "role" to "owner",
                    "addedBy" to currentOwner,
                    "addedAt" to System.currentTimeMillis() / 1000L,
                    "active" to true,
                ),
                SetOptions.merge(),
            ).await()

            try {
                val newUserSnap = db.collection(FirebaseConfig.COL_USERS)
                    .whereEqualTo("email", cleanNew)
                    .limit(1).get().await()
                if (!newUserSnap.isEmpty) {
                    newUserSnap.documents.first().reference.update("role", "owner").await()
                }
                val oldUserSnap = db.collection(FirebaseConfig.COL_USERS)
                    .whereEqualTo("email", currentOwner)
                    .limit(1).get().await()
                if (!oldUserSnap.isEmpty) {
                    oldUserSnap.documents.first().reference.update("role", "super_admin").await()
                }
            } catch (_: Exception) {}

            logAudit(currentOwnerUid, "admin_transfer_owner", "", 0, "$currentOwner->$cleanNew")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun hashEmail(email: String): String =
        email.lowercase().trim().replace("@", "_at_").replace(".", "_dot_")

    // ============================================================
    //  LOGGING
    // ============================================================
    private suspend fun logTransaction(uid: String, type: String, delta: Int, reason: String) {
        try {
            db.collection(FirebaseConfig.COL_TRANSACTIONS).add(
                mapOf(
                    "uid" to uid,
                    "type" to type,
                    "delta" to delta.toLong(),
                    "reason" to reason.take(120),
                    "ts" to System.currentTimeMillis() / 1000L,
                )
            ).await()
        } catch (_: Exception) {}
    }

    private suspend fun logAudit(
        adminUid: String,
        action: String,
        targetUid: String,
        amount: Int,
        reason: String,
    ) {
        try {
            db.collection(FirebaseConfig.COL_AUDIT_LOG).add(
                mapOf(
                    "adminUid" to adminUid,
                    "action" to action,
                    "targetUid" to targetUid,
                    "amount" to amount.toLong(),
                    "reason" to reason,
                    "ts" to System.currentTimeMillis() / 1000L,
                )
            ).await()
        } catch (_: Exception) {}
    }
}