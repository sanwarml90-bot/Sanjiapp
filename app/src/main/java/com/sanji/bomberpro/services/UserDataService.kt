package com.sanji.bomberpro.services

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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
                    otpCost = (doc.getLong("otpCost") ?: 1L).toInt(),
                    dailyBonusCredits = (doc.getLong("dailyBonusCredits") ?: 2L).toInt(),
                    refCredits = (doc.getLong("refCredits") ?: 3L).toInt(),
                    refCoins = (doc.getLong("refCoins") ?: 1L).toInt(),
                    latestVersion = doc.getString("latestVersion") ?: "1.0.0",
                    minVersion = doc.getString("minVersion") ?: "1.0.0",
                    apkUrl = doc.getString("apkUrl") ?: "",
                    changelog = doc.getString("changelog") ?: "",
                    forceUpdate = doc.getBoolean("forceUpdate") ?: false,
                    upiId = doc.getString("upiId") ?: "",
                    upiName = doc.getString("upiName") ?: "",
                    maintenance = doc.getBoolean("maintenance") ?: false,
                    maintenanceMsg = doc.getString("maintenanceMsg") ?: "",
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
        } catch (e: Exception) {
            0
        }
    }

    suspend fun getCoins(uid: String): Int = withContext(Dispatchers.IO) {
        try {
            val snap = db.collection(FirebaseConfig.COL_USERS).document(uid).get().await()
            (snap.getLong("coins") ?: 0L).toInt()
        } catch (e: Exception) {
            0
        }
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
                db.collection(FirebaseConfig.COL_USERS).document(uid)
                    .update("credits", FieldValue.increment(amount.toLong())).await()
                logTransaction(uid, if (isManual) "credit_add_manual" else "credit_add", amount, reason)
                true
            } catch (e: Exception) {
                false
            }
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
            } catch (e: Exception) {
                false
            }
        }

    // ============================================================
    //  COINS
    // ============================================================
    suspend fun addCoins(uid: String, amount: Int, reason: String, isManual: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            if (amount <= 0) return@withContext true
            try {
                db.collection(FirebaseConfig.COL_USERS).document(uid)
                    .update("coins", FieldValue.increment(amount.toLong())).await()
                logTransaction(uid, if (isManual) "coin_add_manual" else "coin_add", amount, reason)
                true
            } catch (e: Exception) {
                false
            }
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
            } catch (e: Exception) {
                false
            }
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
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getBlastRecords(uid: String, limit: Long = 50): List<BlastRecord> =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_BLASTS)
                    .whereEqualTo("uid", uid)
                    .orderBy("ts", Query.Direction.DESCENDING)
                    .limit(limit)
                    .get().await()
                    .documents.map { d ->
                        BlastRecord(
                            id = d.id,
                            uid = d.getString("uid") ?: "",
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
                    }
            } catch (e: Exception) {
                emptyList()
            }
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
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getOtpRecords(uid: String, limit: Long = 100): List<OtpRecord> =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_OTP_FETCHES)
                    .whereEqualTo("uid", uid)
                    .orderBy("ts", Query.Direction.DESCENDING)
                    .limit(limit)
                    .get().await()
                    .documents.map { d ->
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
                    }
            } catch (e: Exception) {
                emptyList()
            }
        }

    // ============================================================
    //  TRANSACTION HISTORY
    // ============================================================
    suspend fun getTransactionRecords(uid: String, limit: Long = 100): List<CreditTransaction> =
        withContext(Dispatchers.IO) {
            try {
                db.collection(FirebaseConfig.COL_TRANSACTIONS)
                    .whereEqualTo("uid", uid)
                    .orderBy("ts", Query.Direction.DESCENDING)
                    .limit(limit)
                    .get().await()
                    .documents.map { d ->
                        CreditTransaction(
                            id = d.id,
                            uid = d.getString("uid") ?: "",
                            type = d.getString("type") ?: "",
                            delta = (d.getLong("delta") ?: 0L).toInt(),
                            reason = d.getString("reason") ?: "",
                            ts = d.getLong("ts") ?: 0L,
                        )
                    }
            } catch (e: Exception) {
                emptyList()
            }
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
                txn.update(
                    userRef,
                    "credits", current + bonus.toLong(),
                    "dailyBonusTs", now,
                )
                bonus
            }.await()

            logTransaction(uid, "daily_bonus", result, "daily-bonus")
            result
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getDailyBonusRemaining(uid: String): Long = withContext(Dispatchers.IO) {
        try {
            val snap = db.collection(FirebaseConfig.COL_USERS).document(uid).get().await()
            val last = snap.getLong("dailyBonusTs") ?: 0L
            val cooldown = 24L * 3600L
            val elapsed = nowSec() - last
            (cooldown - elapsed).coerceAtLeast(0L)
        } catch (e: Exception) {
            0L
        }
    }

    // ============================================================
    //  REFERRAL
    // ============================================================
    suspend fun applyReferralCode(newUid: String, code: String): Result<ReferralResult> =
        withContext(Dispatchers.IO) {
            try {
                val cleanedCode = code.trim().uppercase()
                if (cleanedCode.isEmpty()) {
                    return@withContext Result.failure(Exception("Empty code"))
                }

                val userRef = db.collection(FirebaseConfig.COL_USERS).document(newUid)
                val userSnap = userRef.get().await()
                if (!userSnap.exists()) {
                    return@withContext Result.failure(Exception("User not found"))
                }
                val alreadyReferred = userSnap.getString("referredBy")
                if (!alreadyReferred.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("Already used a referral code"))
                }

                val referrerQuery = db.collection(FirebaseConfig.COL_USERS)
                    .whereEqualTo("referCode", cleanedCode)
                    .limit(1)
                    .get().await()

                if (referrerQuery.isEmpty) {
                    return@withContext Result.failure(Exception("Invalid referral code"))
                }

                val referrerDoc = referrerQuery.documents.first()
                val referrerUid = referrerDoc.id
                if (referrerUid == newUid) {
                    return@withContext Result.failure(Exception("Cannot refer yourself"))
                }

                val settings = getGlobalSettings()
                val rc = settings.refCredits
                val rcoin = settings.refCoins

                val batch = db.batch()
                batch.update(
                    userRef,
                    "credits", FieldValue.increment(rc.toLong()),
                    "coins", FieldValue.increment(rcoin.toLong()),
                    "referredBy", referrerUid,
                )
                batch.update(
                    referrerDoc.reference,
                    "credits", FieldValue.increment(rc.toLong()),
                    "coins", FieldValue.increment(rcoin.toLong()),
                )
                batch.commit().await()

                logTransaction(newUid, "referral_bonus", rc, "referred-by-$referrerUid")
                logTransaction(referrerUid, "referral_bonus", rc, "referred-$newUid")

                Result.success(ReferralResult(referrerUid, rc, rcoin))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getReferralCount(uid: String): Int = withContext(Dispatchers.IO) {
        try {
            db.collection(FirebaseConfig.COL_USERS)
                .whereEqualTo("referredBy", uid)
                .get().await()
                .size()
        } catch (e: Exception) {
            0
        }
    }

    // ============================================================
    //  HELPERS
    // ============================================================
    fun maskNumber(number: String): String {
        if (number.length <= 4) return number
        return number.take(2) + "******" + number.takeLast(4)
    }

    private fun nowSec(): Long = System.currentTimeMillis() / 1000L
}