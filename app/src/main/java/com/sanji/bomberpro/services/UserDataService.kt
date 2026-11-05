package com.sanji.bomberpro.services

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.sanji.bomberpro.FirebaseConfig
import com.sanji.bomberpro.data.AnalyzedUser
import com.sanji.bomberpro.data.BlastRecord
import com.sanji.bomberpro.data.CreditTransaction
import com.sanji.bomberpro.data.DeletedAccount
import com.sanji.bomberpro.data.DuplicateGroup
import com.sanji.bomberpro.data.GlobalSettings
import com.sanji.bomberpro.data.OtpRecord
import com.sanji.bomberpro.data.ReferralResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class UserDataService {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    // ============================================================
    //  SETTINGS
    // ============================================================
    suspend fun getGlobalSettings(): GlobalSettings = withContext(Dispatchers.IO) {
        try {
            val doc = db.collection(FirebaseConfig.COL_SETTINGS)
                .document(FirebaseConfig.DOC_GLOBAL_SETTINGS)
                .get().await()
            if (doc.exists()) {
                GlobalSettings(
                    otpCost = (doc.getLong("otpCost") ?: FirebaseConfig.DEFAULT_OTP_COST.toLong()).toInt(),
                    creditCostPerSms = (doc.getLong("creditCostPerSms") ?: FirebaseConfig.DEFAULT_CREDIT_COST_PER_SMS.toLong()).toInt(),
                    bulkCreditCostPerNumber = (doc.getLong("bulkCreditCostPerNumber") ?: FirebaseConfig.DEFAULT_BULK_CREDIT_COST_PER_NUMBER.toLong()).toInt(),
                    dailyBonusCredits = (doc.getLong("dailyBonusCredits") ?: FirebaseConfig.DEFAULT_DAILY_BONUS_CREDITS.toLong()).toInt(),
                    refCredits = (doc.getLong("refCredits") ?: FirebaseConfig.DEFAULT_REF_CREDITS.toLong()).toInt(),
                    refCoins = (doc.getLong("refCoins") ?: FirebaseConfig.DEFAULT_REF_COINS.toLong()).toInt(),
                    latestVersion = doc.getString("latestVersion") ?: "1.0.0",
                    minVersion = doc.getString("minVersion") ?: "1.0.0",
                    apkUrl = doc.getString("apkUrl") ?: "",
                    changelog = doc.getString("changelog") ?: "",
                    forceUpdate = doc.getBoolean("forceUpdate") ?: false,
                    upiId = doc.getString("upiId") ?: "",
                    upiName = doc.getString("upiName") ?: "",
                    maintenance = doc.getBoolean("maintenance") ?: false,
                    maintenanceMsg = doc.getString("maintenanceMsg") ?: "",
                    termsVersion = (doc.getLong("termsVersion") ?: FirebaseConfig.DEFAULT_TERMS_VERSION.toLong()).toInt(),
                    termsRequired = doc.getBoolean("termsRequired") ?: true,
                    forceJoinEnabled = doc.getBoolean("forceJoinEnabled") ?: false,
                    otpFreshWindowSec = (doc.getLong("otpFreshWindowSec") ?: FirebaseConfig.DEFAULT_OTP_FRESH_WINDOW_SEC),
                    oneDeviceOneAccount = doc.getBoolean("oneDeviceOneAccount") ?: true,
                    specialMsgEnabled = doc.getBoolean("specialMsgEnabled") ?: false,
                    specialMsgVersion = (doc.getLong("specialMsgVersion") ?: 0L).toInt(),
                )
            } else {
                GlobalSettings()
            }
        } catch (e: Exception) {
            GlobalSettings()
        }
    }

    // ============================================================
    //  BALANCE READ
    // ============================================================
    suspend fun getCredits(uid: String): Int = withContext(Dispatchers.IO) {
        try {
            val snap = db.collection(FirebaseConfig.COL_USERS).document(uid).get().await()
            (snap.getLong("credits") ?: 0L).toInt().coerceAtLeast(0)
        } catch (e: Exception) { 0 }
    }

    suspend fun getCoins(uid: String): Int = withContext(Dispatchers.IO) {
        try {
            val snap = db.collection(FirebaseConfig.COL_USERS).document(uid).get().await()
            (snap.getLong("coins") ?: 0L).toInt().coerceAtLeast(0)
        } catch (e: Exception) { 0 }
    }

    fun observeBalance(uid: String): Flow<Pair<Int, Int>> = callbackFlow {
        val ref = db.collection(FirebaseConfig.COL_USERS).document(uid)
        val listener = ref.addSnapshotListener { snap, _ ->
            if (snap != null && snap.exists()) {
                val c = (snap.getLong("credits") ?: 0L).toInt().coerceAtLeast(0)
                val co = (snap.getLong("coins") ?: 0L).toInt().coerceAtLeast(0)
                trySend(c to co)
            }
        }
        awaitClose { listener.remove() }
    }

    // ============================================================
    //  CREDITS — always floor at 0
    // ============================================================
    suspend fun addCredits(
        uid: String,
        amount: Int,
        reason: String,
        isManual: Boolean = false,
    ): Boolean = withContext(Dispatchers.IO) {
        if (amount <= 0) return@withContext true
        try {
            val updates = mutableMapOf<String, Any>(
                "credits" to FieldValue.increment(amount.toLong()),
            )
            if (isManual) updates["manualAddedCredits"] = FieldValue.increment(amount.toLong())
            db.collection(FirebaseConfig.COL_USERS).document(uid).update(updates).await()
            logTransaction(uid, if (isManual) "credit_add_manual" else "credit_add", amount, reason)
            true
        } catch (e: Exception) { false }
    }

    /**
     * Deduct credits safely — transaction-guarded.
     * NEVER goes negative. If requested > current, deducts only what exists.
     * Returns true if ANY amount was deducted (full or partial).
     */
    suspend fun deductCredits(uid: String, amount: Int, reason: String): Boolean =
        withContext(Dispatchers.IO) {
            if (amount <= 0) return@withContext true
            val userRef = db.collection(FirebaseConfig.COL_USERS).document(uid)
            try {
                var actuallyDeducted = 0
                db.runTransaction { txn ->
                    val snap = txn.get(userRef)
                    val current = (snap.getLong("credits") ?: 0L).toInt().coerceAtLeast(0)
                    val cut = amount.coerceAtMost(current).coerceAtLeast(0)
                    actuallyDeducted = cut
                    if (cut > 0) {
                        txn.update(userRef, "credits", (current - cut).toLong())
                    }
                }.await()
                if (actuallyDeducted > 0) {
                    logTransaction(uid, "credit_deduct", -actuallyDeducted, reason)
                }
                actuallyDeducted > 0
            } catch (e: Exception) { false }
        }

    /**
     * Force-deduct — used by blast reserve.
     * If current < amount, THROWS (transaction aborts) — caller should pre-check.
     * Never leaves negative — transaction ensures atomic check.
     */
    suspend fun forceDeductCredits(uid: String, amount: Int, reason: String): Boolean =
        withContext(Dispatchers.IO) {
            if (amount <= 0) return@withContext true
            val userRef = db.collection(FirebaseConfig.COL_USERS).document(uid)
            try {
                db.runTransaction { txn ->
                    val snap = txn.get(userRef)
                    val current = (snap.getLong("credits") ?: 0L).toInt().coerceAtLeast(0)
                    if (current < amount) {
                        throw IllegalStateException("INSUFFICIENT_CREDITS")
                    }
                    txn.update(userRef, "credits", (current - amount).toLong())
                }.await()
                logTransaction(uid, "credit_deduct", -amount, reason)
                true
            } catch (e: Exception) { false }
        }

    /**
     * Wipe ALL credits for a single user. Admin action.
     * Also zeros `manualAddedCredits` to keep pools consistent.
     */
    suspend fun deductAllCreditsFromUser(uid: String, adminUid: String, reason: String = "admin-wipe"): Int =
        withContext(Dispatchers.IO) {
            val userRef = db.collection(FirebaseConfig.COL_USERS).document(uid)
            try {
                var wiped = 0
                db.runTransaction { txn ->
                    val snap = txn.get(userRef)
                    val current = (snap.getLong("credits") ?: 0L).toInt().coerceAtLeast(0)
                    wiped = current
                    txn.update(
                        userRef,
                        "credits", 0L,
                        "manualAddedCredits", 0L,
                    )
                }.await()
                if (wiped > 0) {
                    logTransaction(uid, "credit_deduct", -wiped, reason)
                }
                wiped
            } catch (e: Exception) { 0 }
        }

    // ============================================================
    //  COINS — always floor at 0
    // ============================================================
    suspend fun addCoins(
        uid: String,
        amount: Int,
        reason: String,
        isManual: Boolean = false,
    ): Boolean = withContext(Dispatchers.IO) {
        if (amount <= 0) return@withContext true
        try {
            val updates = mutableMapOf<String, Any>(
                "coins" to FieldValue.increment(amount.toLong()),
            )
            if (isManual) updates["manualAddedCoins"] = FieldValue.increment(amount.toLong())
            db.collection(FirebaseConfig.COL_USERS).document(uid).update(updates).await()
            logTransaction(uid, if (isManual) "coin_add_manual" else "coin_add", amount, reason)
            true
        } catch (e: Exception) { false }
    }

    suspend fun deductCoins(uid: String, amount: Int, reason: String): Boolean =
        withContext(Dispatchers.IO) {
            if (amount <= 0) return@withContext true
            val userRef = db.collection(FirebaseConfig.COL_USERS).document(uid)
            try {
                var actuallyDeducted = 0
                db.runTransaction { txn ->
                    val snap = txn.get(userRef)
                    val current = (snap.getLong("coins") ?: 0L).toInt().coerceAtLeast(0)
                    val cut = amount.coerceAtMost(current).coerceAtLeast(0)
                    actuallyDeducted = cut
                    if (cut > 0) {
                        txn.update(userRef, "coins", (current - cut).toLong())
                    }
                }.await()
                if (actuallyDeducted > 0) {
                    logTransaction(uid, "coin_deduct", -actuallyDeducted, reason)
                }
                actuallyDeducted > 0
            } catch (e: Exception) { false }
        }

    // ============================================================
    //  TRANSFER
    // ============================================================
    suspend fun transferCredits(fromUid: String, toUid: String, amount: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (amount <= 0) return@withContext Result.failure(Exception("Amount must be > 0"))
                if (fromUid == toUid) return@withContext Result.failure(Exception("Cannot transfer to self"))
                val fromRef = db.collection(FirebaseConfig.COL_USERS).document(fromUid)
                val toRef = db.collection(FirebaseConfig.COL_USERS).document(toUid)
                val toSnap = toRef.get().await()
                if (!toSnap.exists()) return@withContext Result.failure(Exception("Recipient not found"))
                db.runTransaction { txn ->
                    val fromSnap = txn.get(fromRef)
                    val current = (fromSnap.getLong("credits") ?: 0L).toInt().coerceAtLeast(0)
                    if (current < amount) throw IllegalStateException("INSUFFICIENT")
                    txn.update(fromRef, "credits", (current - amount).toLong())
                    txn.update(toRef, "credits", FieldValue.increment(amount.toLong()))
                }.await()
                logTransaction(fromUid, "credit_transfer_out", -amount, "to=$toUid")
                logTransaction(toUid, "credit_transfer_in", amount, "from=$fromUid")
                Result.success(Unit)
            } catch (e: Exception) { Result.failure(e) }
        }

    suspend fun transferCoins(fromUid: String, toUid: String, amount: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (amount <= 0) return@withContext Result.failure(Exception("Amount must be > 0"))
                if (fromUid == toUid) return@withContext Result.failure(Exception("Cannot transfer to self"))
                val fromRef = db.collection(FirebaseConfig.COL_USERS).document(fromUid)
                val toRef = db.collection(FirebaseConfig.COL_USERS).document(toUid)
                val toSnap = toRef.get().await()
                if (!toSnap.exists()) return@withContext Result.failure(Exception("Recipient not found"))
                db.runTransaction { txn ->
                    val fromSnap = txn.get(fromRef)
                    val current = (fromSnap.getLong("coins") ?: 0L).toInt().coerceAtLeast(0)
                    if (current < amount) throw IllegalStateException("INSUFFICIENT")
                    txn.update(fromRef, "coins", (current - amount).toLong())
                    txn.update(toRef, "coins", FieldValue.increment(amount.toLong()))
                }.await()
                logTransaction(fromUid, "coin_transfer_out", -amount, "to=$toUid")
                logTransaction(toUid, "coin_transfer_in", amount, "from=$fromUid")
                Result.success(Unit)
            } catch (e: Exception) { Result.failure(e) }
        }

    suspend fun giftCoins(fromUid: String, toUid: String, amount: Int, note: String = ""): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (amount <= 0) return@withContext Result.failure(Exception("Amount must be > 0"))
                if (fromUid == toUid) return@withContext Result.failure(Exception("Cannot gift to self"))
                val fromRef = db.collection(FirebaseConfig.COL_USERS).document(fromUid)
                val toRef = db.collection(FirebaseConfig.COL_USERS).document(toUid)
                val toSnap = toRef.get().await()
                if (!toSnap.exists()) return@withContext Result.failure(Exception("Recipient not found"))
                db.runTransaction { txn ->
                    val fromSnap = txn.get(fromRef)
                    val current = (fromSnap.getLong("coins") ?: 0L).toInt().coerceAtLeast(0)
                    if (current < amount) throw IllegalStateException("INSUFFICIENT")
                    txn.update(fromRef, "coins", (current - amount).toLong())
                    txn.update(toRef, "coins", FieldValue.increment(amount.toLong()))
                }.await()
                logTransaction(fromUid, "coin_gift_out", -amount, "to=$toUid note=${note.take(40)}")
                logTransaction(toUid, "coin_gift_in", amount, "from=$fromUid note=${note.take(40)}")
                Result.success(Unit)
            } catch (e: Exception) { Result.failure(e) }
        }

    // ============================================================
    //  TRANSACTION LOG
    // ============================================================
    suspend fun logTransaction(uid: String, type: String, delta: Int, reason: String) {
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_TRANSACTIONS).add(
                    hashMapOf(
                        "uid" to uid,
                        "type" to type,
                        "delta" to delta.toLong(),
                        "reason" to reason.take(120),
                        "ts" to nowSec(),
                    )
                ).await()
            } catch (_: Exception) {}
        }
    }

    // ============================================================
    //  ACTIVITY LOG
    // ============================================================
    suspend fun logActivity(
        uid: String,
        userName: String,
        userEmail: String,
        action: String,
        targetMasked: String = "",
        targetFull: String = "",
        details: Map<String, Any> = emptyMap(),
    ) {
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_ACTIVITY_LOG).add(
                    hashMapOf(
                        "uid" to uid,
                        "userName" to userName,
                        "userEmail" to userEmail,
                        "action" to action,
                        "targetMasked" to targetMasked,
                        "targetFull" to targetFull,
                        "details" to details,
                        "ts" to nowSec(),
                    )
                ).await()
            } catch (_: Exception) {}
        }
    }

    // ============================================================
    //  MASK
    // ============================================================
    fun maskNumber(number: String): String {
        if (number.length <= 4) return number
        return number.take(2) + "******" + number.takeLast(4)
    }

    private fun nowSec(): Long = System.currentTimeMillis() / 1000L

    // PART 1 ENDS HERE — Part 2 of this file continues in next message
}