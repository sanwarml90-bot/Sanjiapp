package com.sanji.bomberpro.services

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.sanji.bomberpro.FirebaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.SecureRandom

data class RedeemCode(
    val code: String = "",
    val type: String = "credits",
    val amount: Int = 0,
    val usesLeft: Int = 0,
    val plan: String = "",
    val days: Int = 0,
    val createdBy: String = "",
    val createdAt: Long = 0L,
    val usedBy: List<String> = emptyList(),
)

class SettingsService {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val settingsRef get() =
        db.collection(FirebaseConfig.COL_SETTINGS).document(FirebaseConfig.DOC_GLOBAL_SETTINGS)

    // ============================================================
    //  SETTINGS
    // ============================================================
    suspend fun getSettingsMap(): Map<String, Any?> = withContext(Dispatchers.IO) {
        try {
            val snap = settingsRef.get().await()
            if (snap.exists()) snap.data ?: emptyMap() else emptyMap()
        } catch (_: Exception) { emptyMap() }
    }

    suspend fun updateSetting(key: String, value: Any?): Boolean = withContext(Dispatchers.IO) {
        try {
            settingsRef.set(mapOf(key to value), SetOptions.merge()).await()
            true
        } catch (_: Exception) { false }
    }

    suspend fun updateSettings(updates: Map<String, Any?>): Boolean = withContext(Dispatchers.IO) {
        try {
            settingsRef.set(updates, SetOptions.merge()).await()
            true
        } catch (_: Exception) { false }
    }

    // ============================================================
    //  BROADCAST (writes to `broadcasts` + per-user `notifications`)
    // ============================================================
    suspend fun broadcast(
        title: String,
        body: String,
        sentBy: String,
        sentByName: String,
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis() / 1000L
            val users = db.collection(FirebaseConfig.COL_USERS).get().await().documents
            val total = users.size

            // 1. Write broadcast doc
            val bcRef = db.collection("broadcasts").document()
            bcRef.set(
                mapOf(
                    "id" to bcRef.id,
                    "title" to title.take(120),
                    "body" to body.take(2000),
                    "sentBy" to sentBy,
                    "sentByName" to sentByName.take(64),
                    "recipients" to total,
                    "ts" to now,
                )
            ).await()

            // 2. Write per-user notifications (batched 500 at a time)
            val chunks = users.chunked(500)
            for (chunk in chunks) {
                val batch = db.batch()
                for (u in chunk) {
                    val nRef = db.collection("notifications").document()
                    batch.set(
                        nRef,
                        mapOf(
                            "id" to nRef.id,
                            "uid" to u.id,
                            "title" to title.take(120),
                            "body" to body.take(2000),
                            "type" to "broadcast",
                            "broadcastId" to bcRef.id,
                            "read" to false,
                            "ts" to now,
                        )
                    )
                }
                batch.commit().await()
            }

            Result.success(total)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============================================================
    //  REDEEM CODES
    // ============================================================
    suspend fun listRedeemCodes(): List<RedeemCode> = withContext(Dispatchers.IO) {
        try {
            db.collection("redeem_codes").get().await()
                .documents.mapNotNull { d ->
                    val type = d.getString("type") ?: return@mapNotNull null
                    val amount = (d.getLong("amount") ?: 0L).toInt()
                    RedeemCode(
                        code = d.id,
                        type = type,
                        amount = amount,
                        usesLeft = (d.getLong("usesLeft") ?: 0L).toInt(),
                        plan = d.getString("plan") ?: "",
                        days = (d.getLong("days") ?: 0L).toInt(),
                        createdBy = d.getString("createdBy") ?: "",
                        createdAt = d.getLong("createdAt") ?: 0L,
                        usedBy = (d.get("usedBy") as? List<*>)?.map { it.toString() } ?: emptyList(),
                    )
                }
                .sortedByDescending { it.createdAt }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun createRedeemCode(
        type: String,
        amount: Int,
        uses: Int,
        plan: String,
        days: Int,
        createdBy: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val code = generateCode()
            db.collection("redeem_codes").document(code).set(
                mapOf(
                    "code" to code,
                    "type" to type,
                    "amount" to amount.toLong(),
                    "usesLeft" to uses.toLong(),
                    "plan" to plan,
                    "days" to days.toLong(),
                    "createdBy" to createdBy,
                    "createdAt" to System.currentTimeMillis() / 1000L,
                    "usedBy" to emptyList<String>(),
                )
            ).await()
            Result.success(code)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRedeemCode(code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            db.collection("redeem_codes").document(code).delete().await()
            true
        } catch (_: Exception) { false }
    }

    private fun generateCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = SecureRandom()
        val sb = StringBuilder("SJ")
        repeat(10) { sb.append(chars[rnd.nextInt(chars.length)]) }
        return sb.toString()
    }

    // ============================================================
    //  CREDIT PACKS
    // ============================================================
    suspend fun getCreditPacks(): List<CreditPack> = withContext(Dispatchers.IO) {
        try {
            val snap = settingsRef.get().await()
            val arr = snap.get("creditPacks") as? List<*> ?: return@withContext PricingService.DEFAULT_PACKS
            val packs = arr.mapNotNull { item ->
                val m = item as? Map<*, *> ?: return@mapNotNull null
                val id = m["id"] as? String ?: return@mapNotNull null
                CreditPack(
                    id = id,
                    name = (m["name"] as? String) ?: "Pack",
                    credits = (m["credits"] as? Number)?.toInt() ?: 0,
                    priceInr = (m["priceInr"] as? Number)?.toInt() ?: 0,
                )
            }
            if (packs.isEmpty()) PricingService.DEFAULT_PACKS else packs
        } catch (_: Exception) { PricingService.DEFAULT_PACKS }
    }

    suspend fun saveCreditPacks(packs: List<CreditPack>): Boolean = withContext(Dispatchers.IO) {
        try {
            val list = packs.map {
                mapOf(
                    "id" to it.id,
                    "name" to it.name,
                    "credits" to it.credits.toLong(),
                    "priceInr" to it.priceInr.toLong(),
                    "enabled" to true,
                )
            }
            settingsRef.set(mapOf("creditPacks" to list), SetOptions.merge()).await()
            true
        } catch (_: Exception) { false }
    }

    // ============================================================
    //  SUBSCRIPTION PLANS
    // ============================================================
    suspend fun getPlans(): List<SubscriptionPlan> = withContext(Dispatchers.IO) {
        try {
            val snap = settingsRef.get().await()
            val arr = snap.get("plans") as? List<*> ?: return@withContext PricingService.DEFAULT_PLANS.filter { it.id != "free" }
            val plans = arr.mapNotNull { item ->
                val m = item as? Map<*, *> ?: return@mapNotNull null
                val id = m["id"] as? String ?: return@mapNotNull null
                if (id == "free") return@mapNotNull null
                SubscriptionPlan(
                    id = id,
                    name = (m["name"] as? String) ?: id,
                    priceInr = (m["priceInr"] as? Number)?.toInt() ?: 0,
                    durationDays = (m["durationDays"] as? Number)?.toInt() ?: 30,
                    dailyLimit = (m["dailyLimit"] as? Number)?.toInt() ?: 5000,
                )
            }
            if (plans.isEmpty()) PricingService.DEFAULT_PLANS.filter { it.id != "free" } else plans
        } catch (_: Exception) { PricingService.DEFAULT_PLANS.filter { it.id != "free" } }
    }

    suspend fun savePlans(plans: List<SubscriptionPlan>): Boolean = withContext(Dispatchers.IO) {
        try {
            val list = plans.map {
                mapOf(
                    "id" to it.id,
                    "name" to it.name,
                    "priceInr" to it.priceInr.toLong(),
                    "durationDays" to it.durationDays.toLong(),
                    "dailyLimit" to it.dailyLimit.toLong(),
                    "enabled" to true,
                )
            }
            settingsRef.set(mapOf("plans" to list), SetOptions.merge()).await()
            true
        } catch (_: Exception) { false }
    }
}