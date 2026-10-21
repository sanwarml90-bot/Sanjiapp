package com.sanji.bomberpro.services

import com.google.firebase.firestore.FirebaseFirestore
import com.sanji.bomberpro.FirebaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class CreditPack(
    val id: String,
    val name: String,
    val credits: Int,
    val priceInr: Int,
)

data class SubscriptionPlan(
    val id: String,
    val name: String,
    val priceInr: Int,
    val durationDays: Int,
    val dailyLimit: Int,
)

data class RedeemResult(
    val message: String,
    val creditsAdded: Int = 0,
    val coinsAdded: Int = 0,
    val planGranted: String = "",
    val expiresAt: Long = 0L,
)

class PricingService {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val userData = UserDataService()

    suspend fun loadCreditPacks(): List<CreditPack> = withContext(Dispatchers.IO) {
        try {
            val snap = db.collection(FirebaseConfig.COL_SETTINGS)
                .document(FirebaseConfig.DOC_GLOBAL_SETTINGS).get().await()
            val arr = snap.get("creditPacks") as? List<*>
                ?: return@withContext DEFAULT_PACKS
            val packs = arr.mapNotNull { item ->
                val m = item as? Map<*, *> ?: return@mapNotNull null
                val id = m["id"] as? String ?: return@mapNotNull null
                val enabled = m["enabled"] as? Boolean ?: true
                if (!enabled) return@mapNotNull null
                CreditPack(
                    id = id,
                    name = (m["name"] as? String) ?: "Pack",
                    credits = (m["credits"] as? Number)?.toInt() ?: 0,
                    priceInr = (m["priceInr"] as? Number)?.toInt() ?: 0,
                )
            }
            if (packs.isEmpty()) DEFAULT_PACKS else packs
        } catch (_: Exception) {
            DEFAULT_PACKS
        }
    }

    suspend fun loadSubscriptionPlans(): List<SubscriptionPlan> = withContext(Dispatchers.IO) {
        try {
            val snap = db.collection(FirebaseConfig.COL_SETTINGS)
                .document(FirebaseConfig.DOC_GLOBAL_SETTINGS).get().await()
            val arr = snap.get("plans") as? List<*>
                ?: return@withContext DEFAULT_PLANS.filter { it.id != "free" }
            val plans = arr.mapNotNull { item ->
                val m = item as? Map<*, *> ?: return@mapNotNull null
                val id = m["id"] as? String ?: return@mapNotNull null
                if (id == "free") return@mapNotNull null
                val enabled = m["enabled"] as? Boolean ?: true
                if (!enabled) return@mapNotNull null
                SubscriptionPlan(
                    id = id,
                    name = (m["name"] as? String) ?: id,
                    priceInr = (m["priceInr"] as? Number)?.toInt() ?: 0,
                    durationDays = (m["durationDays"] as? Number)?.toInt() ?: 30,
                    dailyLimit = (m["dailyLimit"] as? Number)?.toInt() ?: 5000,
                )
            }
            if (plans.isEmpty()) DEFAULT_PLANS.filter { it.id != "free" } else plans
        } catch (_: Exception) {
            DEFAULT_PLANS.filter { it.id != "free" }
        }
    }

    suspend fun redeemCode(uid: String, code: String): Result<RedeemResult> =
        withContext(Dispatchers.IO) {
            try {
                val cleaned = code.trim().uppercase()
                if (cleaned.isEmpty()) {
                    return@withContext Result.failure(Exception("Empty code"))
                }

                val codeRef = db.collection("redeem_codes").document(cleaned)
                val codeSnap = codeRef.get().await()

                if (!codeSnap.exists()) {
                    return@withContext Result.failure(Exception("Invalid code"))
                }

                val usesLeft = (codeSnap.getLong("usesLeft") ?: 0L).toInt()
                if (usesLeft <= 0) {
                    return@withContext Result.failure(Exception("Code expired"))
                }

                val usedBy = (codeSnap.get("usedBy") as? List<*>)?.map { it.toString() } ?: emptyList()
                if (uid in usedBy) {
                    return@withContext Result.failure(Exception("Already used by you"))
                }

                val type = codeSnap.getString("type") ?: "credits"
                val amount = (codeSnap.getLong("amount") ?: 0L).toInt()
                val plan = codeSnap.getString("plan") ?: ""

                when (type) {
                    "credits" -> {
                        userData.addCredits(uid, amount, "redeem-$cleaned", false)
                        userData.logTransaction(uid, "redeem_credit", amount, "code=$cleaned")
                    }
                    "coins" -> {
                        userData.addCoins(uid, amount, "redeem-$cleaned", false)
                        userData.logTransaction(uid, "redeem_coin", amount, "code=$cleaned")
                    }
                    "subscription" -> {
                        val now = System.currentTimeMillis() / 1000L
                        val expire = now + 30L * 86400L
                        db.collection(FirebaseConfig.COL_USERS).document(uid)
                            .update(
                                mapOf(
                                    "subscription.plan" to plan,
                                    "subscription.expiresAt" to expire,
                                    "subscription.startedAt" to now,
                                )
                            ).await()
                    }
                }

                codeRef.update(
                    "usesLeft", (usesLeft - 1).toLong(),
                    "usedBy", usedBy + uid,
                ).await()

                Result.success(
                    RedeemResult(
                        message = "Redeemed successfully",
                        creditsAdded = if (type == "credits") amount else 0,
                        coinsAdded = if (type == "coins") amount else 0,
                        planGranted = if (type == "subscription") plan else "",
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    companion object {
        val DEFAULT_PACKS = listOf(
            CreditPack("p1", "100 Credits", 100, 49),
            CreditPack("p2", "500 Credits", 500, 199),
            CreditPack("p3", "2000 Credits", 2000, 699),
        )
        val DEFAULT_PLANS = listOf(
            SubscriptionPlan("free", "Free", 0, 0, 50),
            SubscriptionPlan("pro", "Pro", 99, 30, 5000),
            SubscriptionPlan("elite", "Elite", 299, 30, 20000),
        )
    }
}