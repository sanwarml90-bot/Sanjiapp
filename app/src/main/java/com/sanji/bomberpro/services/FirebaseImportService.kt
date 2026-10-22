package com.sanji.bomberpro.services

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.sanji.bomberpro.FirebaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class ImportResult(
    val added: Int,
    val skippedExisting: Int,
    val skippedDuplicate: Int,
    val failed: Int,
    val failureSummary: String,
    val totalUrls: Int,
    val existingAfter: Int,
)

class FirebaseImportService {

    private val db = FirebaseFirestore.getInstance()
    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // bot _URL_REGEX = https?://[^\s<>"')\]}\]+
    private val urlRegex = Regex("""https?://[^\s<>"')\]}\]+""")

    private val excludedHosts = listOf(
        "t.me", "telegram", "github.com", "githubusercontent",
        "youtube.com", "youtu.be",
    )

    // ============================================================
    //  IMPORT — from Uri (file picker)
    // ============================================================
    suspend fun importFromUri(
        adminUid: String,
        uri: Uri,
        contentResolver: ContentResolver,
        onProgress: suspend (phase: String, current: Int, total: Int, added: Int) -> Unit = { _, _, _, _ -> },
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        try {
            val text = try {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: return@withContext Result.failure(Exception("Cannot open file"))
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("Read failed: ${e.message}"))
            }

            if (text.length > 50 * 1024 * 1024) {
                return@withContext Result.failure(Exception("File too large (>50MB)"))
            }

            importFromText(adminUid, text, onProgress)
        } catch (e: Exception) {
            Log.e(TAG, "importFromUri failed", e)
            Result.failure(e)
        }
    }

    // ============================================================
    //  IMPORT — from raw text
    // ============================================================
    suspend fun importFromText(
        adminUid: String,
        text: String,
        onProgress: suspend (phase: String, current: Int, total: Int, added: Int) -> Unit = { _, _, _, _ -> },
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        try {
            onProgress("extracting", 0, 0, 0)
            val urls = extractUrls(text).distinct()
            if (urls.isEmpty()) {
                return@withContext Result.failure(Exception("No URLs found in input"))
            }

            // load existing
            onProgress("loading", 0, urls.size, 0)
            val existingSnap = db.collection(FirebaseConfig.COL_FIREBASES).get().await()
            val existing = existingSnap.documents.mapNotNull {
                it.getString("url")?.trimEnd('/')
            }.toSet()

            val toCheck = urls.filter { it.trimEnd('/') !in existing }
            val skippedExisting = urls.size - toCheck.size

            if (toCheck.isEmpty()) {
                return@withContext Result.success(
                    ImportResult(
                        added = 0,
                        skippedExisting = skippedExisting,
                        skippedDuplicate = 0,
                        failed = 0,
                        failureSummary = "",
                        totalUrls = urls.size,
                        existingAfter = existing.size,
                    )
                )
            }

            // validate in parallel (30 concurrent — matches bot)
            onProgress("validating", 0, toCheck.size, 0)
            val valid = mutableListOf<String>()
            val failureReasons = mutableMapOf<String, Int>()
            val counter = AtomicInteger(0)
            val sem = Semaphore(30)

            coroutineScope {
                toCheck.map { url ->
                    async {
                        sem.withPermit {
                            val res = checkFirebaseUrl(url)
                            if (res.isSuccess) valid.add(url.trimEnd('/'))
                            else {
                                val reason = categorizeFailure(res.exceptionOrNull()?.message ?: "unknown")
                                synchronized(failureReasons) {
                                    failureReasons[reason] = (failureReasons[reason] ?: 0) + 1
                                }
                            }
                            val c = counter.incrementAndGet()
                            if (c % 5 == 0 || c == toCheck.size) {
                                onProgress("validating", c, toCheck.size, valid.size)
                            }
                        }
                    }
                }.awaitAll()
            }

            // insert valid — dedupe locally again (race-safe)
            onProgress("inserting", 0, valid.size, 0)
            val uniqueValid = valid.toSet().toList()
            val batch = db.batch()
            var inserted = 0
            for (url in uniqueValid) {
                val id = generateFirebaseId()
                val ref = db.collection(FirebaseConfig.COL_FIREBASES).document(id)
                val label = try {
                    Uri.parse(url).host?.take(30) ?: "Firebase"
                } catch (_: Exception) {
                    "Firebase"
                }
                batch.set(
                    ref,
                    mapOf(
                        "id" to id,
                        "url" to url,
                        "label" to label,
                        "enabled" to true,
                        "addedAt" to System.currentTimeMillis() / 1000L,
                    )
                )
                inserted++
                if (inserted % 400 == 0) {
                    batch.commit().await()
                }
            }
            if (inserted % 400 != 0) batch.commit().await()

            // final count
            val finalCount = existingSnap.size() + inserted

            // audit
            try {
                db.collection(FirebaseConfig.COL_AUDIT_LOG).add(
                    mapOf(
                        "adminUid" to adminUid,
                        "action" to "fb_bulk_import",
                        "targetUid" to "",
                        "amount" to 0L,
                        "reason" to "added=$inserted skipped=${skippedExisting + (valid.size - inserted)} failed=${toCheck.size - valid.size}",
                        "ts" to System.currentTimeMillis() / 1000L,
                    )
                ).await()
            } catch (_: Exception) {}

            val failureSummary = failureReasons.entries
                .sortedByDescending { it.value }
                .joinToString(" · ") { "${it.key}: ${it.value}" }

            onProgress("done", toCheck.size, toCheck.size, inserted)

            Result.success(
                ImportResult(
                    added = inserted,
                    skippedExisting = skippedExisting,
                    skippedDuplicate = valid.size - inserted,
                    failed = toCheck.size - valid.size,
                    failureSummary = failureSummary,
                    totalUrls = urls.size,
                    existingAfter = finalCount,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "importFromText failed", e)
            Result.failure(e)
        }
    }

    // ============================================================
    //  VALIDATE — GET {url}/clients.json?shallow=true
    // ============================================================
    private suspend fun checkFirebaseUrl(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanUrl = url.trimEnd('/')
        try {
            val req = Request.Builder()
                .url("$cleanUrl/clients.json?shallow=true")
                .get()
                .build()

            http.newCall(req).execute().use { resp ->
                when {
                    resp.isSuccessful -> Result.success(Unit)
                    resp.code == 401 -> Result.failure(Exception("auth"))
                    resp.code == 403 -> Result.failure(Exception("forbidden"))
                    resp.code == 404 -> Result.failure(Exception("notfound"))
                    resp.code == 429 -> Result.failure(Exception("ratelimit"))
                    resp.code in 500..599 -> Result.failure(Exception("server"))
                    else -> Result.failure(Exception("http_${resp.code}"))
                }
            }
        } catch (e: Exception) {
            val m = (e.message ?: "").lowercase()
            val reason = when {
                m.contains("timeout") -> "timeout"
                m.contains("unknown host") || m.contains("dns") -> "network"
                m.contains("ssl") || m.contains("certificate") -> "network"
                else -> "network"
            }
            Result.failure(Exception(reason))
        }
    }

    private fun categorizeFailure(raw: String): String = when {
        raw.contains("timeout") -> "timeout"
        raw.contains("auth") -> "auth"
        raw.contains("forbidden") -> "forbidden"
        raw.contains("notfound") -> "notfound"
        raw.contains("ratelimit") -> "ratelimit"
        raw.contains("server") -> "server"
        raw.contains("network") -> "network"
        raw.startsWith("http_") -> raw
        else -> "unknown"
    }

    // ============================================================
    //  EXTRACT URLs
    // ============================================================
    fun extractUrls(text: String): List<String> {
        val matches = urlRegex.findAll(text)
        val seen = HashSet<String>()
        val out = mutableListOf<String>()
        for (m in matches) {
            var u = m.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'')
            if (u.isBlank()) continue
            try {
                val host = Uri.parse(u).host?.lowercase() ?: continue
                if (excludedHosts.any { host.contains(it) }) continue
            } catch (_: Exception) {
                continue
            }
            if (u in seen) continue
            seen.add(u)
            out.add(u)
        }
        return out
    }

    // ============================================================
    //  EXPORT — JSON string (bot parity)
    // ============================================================
    suspend fun exportFirebases(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val snap = db.collection(FirebaseConfig.COL_FIREBASES)
                .orderBy("addedAt")
                .get().await()

            val list = snap.documents.mapIndexed { i, d ->
                val url = d.getString("url") ?: ""
                val label = d.getString("label") ?: ""
                val addedAt = d.getLong("addedAt") ?: 0L
                """    {"index": ${i + 1}, "id": "${esc(d.id)}", "url": "${esc(url)}", "label": "${esc(label)}", "added_at": $addedAt}"""
            }

            val json = buildString {
                appendLine("{")
                appendLine("  \"version\": \"app-1.0\",")
                appendLine("  \"exported_at\": ${System.currentTimeMillis() / 1000L},")
                appendLine("  \"total\": ${list.size},")
                appendLine("  \"firebases\": [")
                list.forEachIndexed { i, line ->
                    append(line)
                    if (i < list.size - 1) append(",")
                    appendLine()
                }
                appendLine("  ]")
                appendLine("}")
            }

            Result.success(json)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private fun generateFirebaseId(): String =
        "${System.currentTimeMillis()}${(100..999).random()}"

    companion object {
        private const val TAG = "FirebaseImportService"
    }
}