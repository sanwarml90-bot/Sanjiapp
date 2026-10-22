package com.sanji.bomberpro.services

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.sanji.bomberpro.FirebaseConfig
import com.sanji.bomberpro.data.BlastRecord
import com.sanji.bomberpro.data.CreditTransaction
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
            (snap.getLong("credits") ?: 0L).toInt()
        } catch (e: Exception) { 0 }
    }

    suspend fun getCoins(uid: String): Int = withContext(Dispatchers.IO) {
        try {
            val snap = db.collection(FirebaseConfig.COL_USERS).document(uid).get().await()
            (snap.getLong("coins") ?: 0L).toInt()
        } catch (e: Exception) { 0 }
    }

    fun observeBalance(uid: String): Flow<Pair<Int, Int>> = callbackFlow {
        val ref = db.collection(FirebaseConfig.COL_USERS).document(uid)
        val listener = ref.addSnapshotListener { snap, _ ->
            if (snap != null && snap.exists()) {
                val c = (snap.getLong("credits") ?: 0L).toInt()
                val co = (snap.getLong("coins") ?: 0L).toInt()
                trySend(c to co)
            }
        }
        awaitClose { listener.remove() }
    }

    // ============================================================
    //  CREDITS
    // ============================================================
    suspend fun addCredits(uid: String, amount: Int, reason: String, isManual: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
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

    suspend fun deductCredits(uid: String, amount: Int, reason: String): Boolean =
        withContext(Dispatchers.IO) {
            if (amount <= 0) return@withContext true
            val userRef = db.collection(FirebaseConfig.COL_USERS).document(uid)
            try {
                db.runTransaction { txn ->
                    val snap = txn.get(userRef)
                    val current = (snap.getLong("credits") ?: 0L).toInt()
                    if (current < amount) throw IllegalStateException("INSUFFICIENT")
                    txn.update(userRef, "credits", (current - amount).toLong())
                }.await()
                logTransaction(uid, "credit_deduct", -amount, reason)
                true
            } catch (e: Exception) { false }
        }

    // ============================================================
    //  COINS
    // ============================================================
    suspend fun addCoins(uid: String, amount: Int, reason: String, isManual: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
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
                db.runTransaction { txn ->
                    val snap = txn.get(userRef)
                    val current = (snap.getLong("coins") ?: 0L).toInt()
                    if (current < amount) throw IllegalStateException("INSUFFICIENT")
                    txn.update(userRef, "coins", (current - amount).toLong())
                }.await()
                logTransaction(uid, "coin_deduct", -amount, reason)
                true
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
                    val current = (fromSnap.getLong("credits") ?: 0L).toInt()
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
                    val current = (fromSnap.getLong("coins") ?: 0L).toInt()
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
                    val current = (fromSnap.getLong("coins") ?: 0L).toInt()
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
    //  BLAST HISTORY
    // ============================================================
    suspend fun saveBlastHistory(
        uid: String,
        targetFull: String,
        message: String,
        requested: Int,
        sent: Int,
        failed: Int,
        durationSec: Long,
        speed: String,
        devicePool: List<String>,
        status: String = "completed",
    ): String? = withContext(Dispatchers.IO) {
        try {
            val ref = db.collection(FirebaseConfig.COL_BLASTS).document()
            val masked = maskNumber(targetFull)
            ref.set(
                hashMapOf(
                    "id" to ref.id,
                    "uid" to uid,
                    "type" to "single",
                    "targetFull" to targetFull,
                    "targetMasked" to masked,
                    "message" to message.take(500),
                    "requested" to requested,
                    "sent" to sent,
                    "failed" to failed,
                    "durationSec" to durationSec,
                    "speed" to speed,
                    "devicePool" to devicePool,
                    "status" to status,
                    "ts" to nowSec(),
                )
            ).await()

            db.collection(FirebaseConfig.COL_USERS).document(uid)
                .update(
                    "totalBlasts", FieldValue.increment(1L),
                    "totalSms", FieldValue.increment(sent.toLong()),
                ).await()

            ref.id
        } catch (e: Exception) { null }
    }

    // NO composite index required — filter + sort client-side
    suspend fun getBlastRecords(uid: String, limit: Long = 50): List<BlastRecord> =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_BLASTS)
                    .whereEqualTo("uid", uid)
                    .limit(500)
                    .get().await()
                    .documents.mapNotNull { d ->
                        try {
                            BlastRecord(
                                id = d.id,
                                uid = d.getString("uid") ?: "",
                                type = d.getString("type") ?: "single",
                                targetFull = d.getString("targetFull") ?: "",
                                targetMasked = d.getString("targetMasked") ?: "",
                                message = d.getString("message") ?: "",
                                requested = (d.getLong("requested") ?: 0L).toInt(),
                                sent = (d.getLong("sent") ?: 0L).toInt(),
                                failed = (d.getLong("failed") ?: 0L).toInt(),
                                durationSec = d.getLong("durationSec") ?: 0L,
                                speed = d.getString("speed") ?: "",
                                devicePool = (d.get("devicePool") as? List<*>)?.map { it.toString() } ?: emptyList(),
                                status = d.getString("status") ?: "completed",
                                ts = d.getLong("ts") ?: 0L,
                            )
                        } catch (_: Exception) { null }
                    }
                    .sortedByDescending { it.ts }
                    .take(limit.toInt())
            } catch (e: Exception) { emptyList() }
        }

    // ============================================================
    //  BULK BLAST HISTORY
    // ============================================================
    suspend fun saveBulkBlastHistory(
        uid: String,
        numbers: List<String>,
        message: String,
        perNumberCount: Int,
        power: Int,
        totalSent: Int,
        totalFailed: Int,
        durationSec: Long,
        speed: String,
        status: String = "completed",
    ): String? = withContext(Dispatchers.IO) {
        try {
            val ref = db.collection(FirebaseConfig.COL_BLASTS).document()
            ref.set(
                hashMapOf(
                    "id" to ref.id,
                    "uid" to uid,
                    "type" to "bulk",
                    "numbersCount" to numbers.size,
                    "targetsMasked" to numbers.take(50).map { maskNumber(it) },
                    "message" to message.take(500),
                    "perNumberCount" to perNumberCount,
                    "power" to power,
                    "requested" to (numbers.size * perNumberCount * power),
                    "sent" to totalSent,
                    "failed" to totalFailed,
                    "durationSec" to durationSec,
                    "speed" to speed,
                    "status" to status,
                    "ts" to nowSec(),
                )
            ).await()

            db.collection(FirebaseConfig.COL_USERS).document(uid)
                .update(
                    "totalBlasts", FieldValue.increment(1L),
                    "totalSms", FieldValue.increment(totalSent.toLong()),
                ).await()

            ref.id
        } catch (e: Exception) { null }
    }

    suspend fun getBulkBlastRecords(uid: String, limit: Long = 20): List<BlastRecord> =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_BLASTS)
                    .whereEqualTo("uid", uid)
                    .limit(200)
                    .get().await()
                    .documents.mapNotNull { d ->
                        try {
                            BlastRecord(
                                id = d.id,
                                uid = d.getString("uid") ?: "",
                                type = d.getString("type") ?: "bulk",
                                targetFull = "",
                                targetMasked = "${(d.getLong("numbersCount") ?: 0L)} numbers",
                                message = d.getString("message") ?: "",
                                requested = (d.getLong("requested") ?: 0L).toInt(),
                                sent = (d.getLong("sent") ?: 0L).toInt(),
                                failed = (d.getLong("failed") ?: 0L).toInt(),
                                durationSec = d.getLong("durationSec") ?: 0L,
                                speed = d.getString("speed") ?: "",
                                devicePool = emptyList(),
                                status = d.getString("status") ?: "completed",
                                ts = d.getLong("ts") ?: 0L,
                            )
                        } catch (_: Exception) { null }
                    }
                    .filter { it.type == "bulk" }
                    .sortedByDescending { it.ts }
                    .take(limit.toInt())
            } catch (e: Exception) { emptyList() }
        }

    // ============================================================
    //  OTP HISTORY
    // ============================================================
    suspend fun saveOtpHistory(
        uid: String,
        otp: String,
        sender: String,
        deviceName: String,
        deviceShort: String,
        devicePhone: String,
        firebaseLabel: String,
        mode: String,
        cost: Int,
        refunded: Boolean = false,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val ref = db.collection(FirebaseConfig.COL_OTP_FETCHES).document()
            ref.set(
                hashMapOf(
                    "id" to ref.id,
                    "uid" to uid,
                    "otp" to otp.take(32),
                    "sender" to sender.take(64),
                    "deviceName" to deviceName.take(32),
                    "deviceShort" to deviceShort.take(20),
                    "devicePhone" to devicePhone,
                    "firebaseLabel" to firebaseLabel.take(32),
                    "mode" to mode,
                    "cost" to cost.toLong(),
                    "refunded" to refunded,
                    "ts" to nowSec(),
                )
            ).await()

            db.collection(FirebaseConfig.COL_USERS).document(uid)
                .update("totalOtps", FieldValue.increment(1L)).await()

            ref.id
        } catch (e: Exception) { null }
    }

    suspend fun getOtpRecords(uid: String, limit: Long = 100): List<OtpRecord> =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_OTP_FETCHES)
                    .whereEqualTo("uid", uid)
                    .limit(500)
                    .get().await()
                    .documents.mapNotNull { d ->
                        try {
                            OtpRecord(
                                id = d.id,
                                uid = d.getString("uid") ?: "",
                                otp = d.getString("otp") ?: "",
                                sender = d.getString("sender") ?: "",
                                deviceName = d.getString("deviceName") ?: "",
                                deviceShort = d.getString("deviceShort") ?: "",
                                devicePhone = d.getString("devicePhone") ?: "",
                                firebaseLabel = d.getString("firebaseLabel") ?: "",
                                mode = d.getString("mode") ?: "",
                                cost = (d.getLong("cost") ?: 0L).toInt(),
                                refunded = d.getBoolean("refunded") ?: false,
                                ts = d.getLong("ts") ?: 0L,
                            )
                        } catch (_: Exception) { null }
                    }
                    .sortedByDescending { it.ts }
                    .take(limit.toInt())
            } catch (e: Exception) { emptyList() }
        }

    // ============================================================
    //  TRANSACTION HISTORY
    // ============================================================
    suspend fun getTransactionRecords(uid: String, limit: Long = 100): List<CreditTransaction> =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_TRANSACTIONS)
                    .whereEqualTo("uid", uid)
                    .limit(500)
                    .get().await()
                    .documents.mapNotNull { d ->
                        try {
                            CreditTransaction(
                                id = d.id,
                                uid = d.getString("uid") ?: "",
                                type = d.getString("type") ?: "",
                                delta = (d.getLong("delta") ?: 0L).toInt(),
                                reason = d.getString("reason") ?: "",
                                ts = d.getLong("ts") ?: 0L,
                            )
                        } catch (_: Exception) { null }
                    }
                    .sortedByDescending { it.ts }
                    .take(limit.toInt())
            } catch (e: Exception) { emptyList() }
        }

    // ============================================================
    //  DAILY BONUS
    // ============================================================
    suspend fun tryClaimDailyBonus(uid: String): Int? = withContext(Dispatchers.IO) {
        val settings = getGlobalSettings()
        val bonus = settings.dailyBonusCredits
        if (bonus <= 0) return@withContext null
        val userRef = db.collection(FirebaseConfig.COL_USERS).document(uid)
        val cooldown = 24L * 3600L
        try {
            val result = db.runTransaction { txn ->
                val snap = txn.get(userRef)
                val last = snap.getLong("dailyBonusTs") ?: 0L
                val now = nowSec()
                if (now - last < cooldown) throw IllegalStateException("COOLDOWN")
                val current = snap.getLong("credits") ?: 0L
                txn.update(userRef, "credits", current + bonus.toLong(), "dailyBonusTs", now)
                bonus
            }.await()
            logTransaction(uid, "daily_bonus", result, "daily-bonus")
            result
        } catch (e: Exception) { null }
    }

    suspend fun getDailyBonusRemaining(uid: String): Long = withContext(Dispatchers.IO) {
        try {
            val snap = db.collection(FirebaseConfig.COL_USERS).document(uid).get().await()
            val last = snap.getLong("dailyBonusTs") ?: 0L
            val cooldown = 24L * 3600L
            val elapsed = nowSec() - last
            (cooldown - elapsed).coerceAtLeast(0L)
        } catch (e: Exception) { 0L }
    }

    // ============================================================
    //  REFERRAL
    // ============================================================
    suspend fun applyReferralCode(newUid: String, code: String): Result<ReferralResult> =
        withContext(Dispatchers.IO) {
            try {
                val cleanedCode = code.trim().uppercase()
                if (cleanedCode.isEmpty()) return@withContext Result.failure(Exception("Empty code"))
                val userRef = db.collection(FirebaseConfig.COL_USERS).document(newUid)
                val userSnap = userRef.get().await()
                if (!userSnap.exists()) return@withContext Result.failure(Exception("User not found"))
                val alreadyReferred = userSnap.getString("referredBy")
                if (!alreadyReferred.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("Already used a referral code"))
                }
                val referrerQuery = db.collection(FirebaseConfig.COL_USERS)
                    .whereEqualTo("referCode", cleanedCode)
                    .limit(1).get().await()
                if (referrerQuery.isEmpty) return@withContext Result.failure(Exception("Invalid referral code"))
                val referrerDoc = referrerQuery.documents.first()
                val referrerUid = referrerDoc.id
                if (referrerUid == newUid) return@withContext Result.failure(Exception("Cannot refer yourself"))
                val settings = getGlobalSettings()
                val rc = settings.refCredits
                val rcoin = settings.refCoins
                db.runTransaction { txn ->
                    val freshSnap = txn.get(userRef)
                    val freshReferred = freshSnap.getString("referredBy")
                    if (!freshReferred.isNullOrBlank()) throw IllegalStateException("ALREADY_REFERRED")
                    txn.update(
                        userRef,
                        "credits", FieldValue.increment(rc.toLong()),
                        "coins", FieldValue.increment(rcoin.toLong()),
                        "referredBy", referrerUid,
                    )
                    txn.update(
                        referrerDoc.reference,
                        "credits", FieldValue.increment(rc.toLong()),
                        "coins", FieldValue.increment(rcoin.toLong()),
                    )
                }.await()
                logTransaction(newUid, "referral_bonus", rc, "referred-by-$referrerUid")
                logTransaction(referrerUid, "referral_bonus", rc, "referred-$newUid")
                Result.success(ReferralResult(referrerUid, rc, rcoin))
            } catch (e: Exception) { Result.failure(e) }
        }

    suspend fun getReferralCount(uid: String): Int = withContext(Dispatchers.IO) {
        try {
            db.collection(FirebaseConfig.COL_USERS)
                .whereEqualTo("referredBy", uid)
                .get().await().size()
        } catch (e: Exception) { 0 }
    }

    fun maskNumber(number: String): String {
        if (number.length <= 4) return number
        return number.take(2) + "******" + number.takeLast(4)
    }

    private fun nowSec(): Long = System.currentTimeMillis() / 1000L
}