package com.sanji.bomberpro.services

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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
                    .update("credits", FieldValue.increment(amount.toLong())).await()
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
                val current = (ref.get().await().getLong("credits") ?: 0L).toInt()
                if (current < amount) return@withContext false
                ref.update("credits", (current - amount).toLong()).await()
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
                    .update("coins", FieldValue.increment(amount.toLong())).await()
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
                val current = (ref.get().await().getLong("coins") ?: 0L).toInt()
                if (current < amount) return@withContext false
                ref.update("coins", (current - amount).toLong()).await()
                logTransaction(uid, "coin_deduct", -amount, "admin-deduct")
                logAudit(adminUid, "admin_coin_deduct", uid, amount, "")
                true
            } catch (e: Exception) {
                false
            }
        }

    // ============================================================
    //  BAN / UNBAN
    // ============================================================
    suspend fun banUser(uid: String, reason: String, adminUid: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_USERS).document(uid)
                    .update(
                        mapOf(
                            "banned" to true,
                            "banReason" to reason.take(200),
                        )
                    ).await()
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
                    .update(
                        mapOf(
                            "banned" to false,
                            "banReason" to "",
                        )
                    ).await()
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
                if (limit == null) {
                    ref.update("maxSendLimit", FieldValue.delete()).await()
                } else {
                    ref.update("maxSendLimit", limit.toLong()).await()
                }
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
                if (limit == null) {
                    ref.update("maxOtpLimit", FieldValue.delete()).await()
                } else {
                    ref.update("maxOtpLimit", limit.toLong()).await()
                }
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