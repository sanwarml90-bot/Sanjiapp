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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class ImportResult(
    val added: Int,
    val skippedExisting: Int,
    val skippedDuplicate: Int,
    val failed: Int,
    val failureSummary: String,
    val totalUrls: Int,
    val existingAfter: Int,
    val failures: List<Pair<String, String>> = emptyList(),
)

data class ValidationResult(
    val valid: List<String>,
    val failed: List<Pair<String, String>>,
    val cancelled: Boolean = false,
)

class FirebaseImportService {

    private val db = FirebaseFirestore.getInstance()
    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // SAFE regex — no brackets, parens, or braces inside the character class.
    // Matches http(s):// followed by any char that is NOT whitespace / < / > / " / ' / `
    private val defaultUrlRegex: Regex = try {
        Regex("""https?://[^\s<>"'`]+""", RegexOption.IGNORE_CASE)
    } catch (e: Exception) {
        Log.e(TAG, "regex compile failed — using ultra-simple fallback", e)
        Regex("""https?://\S+""")
    }

    private val excludedHosts = listOf(
        "t.me", "telegram.org", "telegram.me",
        "github.com", "githubusercontent.com",
        "youtube.com", "youtu.be",
        "play.google.com", "apps.apple.com",
    )

    private val urlTrailingTrim = charArrayOf(
        '.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'', '`', '#',
    )

    // ============================================================
    //  EXTRACT
    // ============================================================
    fun extractUrls(text: String, customPattern: String? = null): List<String> {
        if (text.isBlank()) return emptyList()

        val regex = try {
            if (!customPattern.isNullOrBlank()) Regex(customPattern, RegexOption.IGNORE_CASE)
            else defaultUrlRegex
        } catch (e: Exception) {
            Log.w(TAG, "custom url pattern invalid — falling back to default", e)
            defaultUrlRegex
        }

        val seen = LinkedHashSet<String>()
        for (match in regex.findAll(text)) {
            var u = match.value.trim()
            u = u.trimEnd(*urlTrailingTrim)
            if (u.isEmpty()) continue

            val host = try { Uri.parse(u).host?.lowercase() } catch (_: Exception) { null }
                ?: continue
            if (host.isBlank()) continue
            if (excludedHosts.any { host.contains(it) }) continue

            seen.add(u)
        }
        return seen.toList()
    }

    // ============================================================
    //  READ URI
    // ============================================================
    suspend fun readUriText(
        uri: Uri,
        contentResolver: ContentResolver,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val text = contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: return@withContext Result.failure(Exception("Cannot open file"))
            if (text.length > 50 * 1024 * 1024) {
                return@withContext Result.failure(Exception("File too large (>50MB)"))
            }
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(Exception("Read failed: ${e.message}"))
        }
    }

    // ============================================================
    //  VALIDATE
    // ============================================================
    suspend fun validateUrls(
        urls: List<String>,
        existingUrls: Set<String>,
        cancel: AtomicBoolean,
        onProgress: suspend (current: Int, total: Int, valid: Int, failed: Int) -> Unit,
    ): ValidationResult = withContext(Dispatchers.IO) {
        val normalized = urls
            .map { it.trim().trimEnd('/') }
            .filter { it.isNotBlank() }
            .distinct()

        val toCheck = normalized.filter { it.lowercase() !in existingUrls }
        if (toCheck.isEmpty()) {
            return@withContext ValidationResult(valid = emptyList(), failed = emptyList())
        }

        val valid = java.util.Collections.synchronizedList(mutableListOf<String>())
        val failed = java.util.Collections.synchronizedList(mutableListOf<Pair<String, String>>())
        val counter = AtomicInteger(0)
        val sem = Semaphore(30)

        coroutineScope {
            toCheck.map { url ->
                async {
                    if (cancel.get()) return@async
                    sem.withPermit {
                        if (cancel.get()) return@withPermit
                        val res = checkFirebaseUrl(url)
                        if (res.isSuccess) {
                            valid.add(url)
                        } else {
                            val reason = res.exceptionOrNull()?.message ?: "unknown"
                            failed.add(url to categorizeFailure(reason))
                        }
                        val c = counter.incrementAndGet()
                        if (c % 5 == 0 || c == toCheck.size) {
                            onProgress(c, toCheck.size, valid.size, failed.size)
                        }
                    }
                }
            }.awaitAll()
        }

        ValidationResult(
            valid = valid.toList(),
            failed = failed.toList(),
            cancelled = cancel.get(),
        )
    }

    // ============================================================
    //  LOAD EXISTING
    // ============================================================
    suspend fun loadExistingUrls(): Set<String> = withContext(Dispatchers.IO) {
        try {
            db.collection(FirebaseConfig.COL_FIREBASES).get().await()
                .documents.mapNotNull {
                    it.getString("url")?.trim()?.trimEnd('/')?.lowercase()
                }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    // ============================================================
    //  DETERMINISTIC DOC ID
    // ============================================================
    private fun firebaseDocId(url: String): String {
        val normalized = url.trim().trimEnd('/').lowercase()
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(normalized.toByteArray(Charsets.UTF_8))
            val sb = StringBuilder(digest.size * 2)
            for (b in digest) {
                val v = b.toInt() and 0xFF
                sb.append(HEX[v ushr 4])
                sb.append(HEX[v and 0x0F])
            }
            sb.toString().take(28)
        } catch (_: Exception) {
            normalized.hashCode().toString()
        }
    }

    // ============================================================
    //  INSERT
    // ============================================================
    suspend fun insertValidated(
        adminUid: String,
        validUrls: List<String>,
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val unique = validUrls
                .map { it.trim().trimEnd('/') }
                .filter { it.isNotBlank() }
                .distinct()

            if (unique.isEmpty()) return@withContext Result.success(0)

            var inserted = 0
            var skipped = 0
            var batch = db.batch()
            var inBatch = 0

            for (url in unique) {
                val docId = firebaseDocId(url)
                val ref = db.collection(FirebaseConfig.COL_FIREBASES).document(docId)

                val existingSnap = try { ref.get().await() } catch (_: Exception) { null }
                if (existingSnap != null && existingSnap.exists()) {
                    skipped++
                    continue
                }

                val label = try {
                    Uri.parse(url).host?.take(30) ?: "Firebase"
                } catch (_: Exception) {
                    "Firebase"
                }

                batch.set(
                    ref,
                    mapOf(
                        "id" to docId,
                        "url" to url,
                        "label" to label,
                        "enabled" to true,
                        "addedAt" to System.currentTimeMillis() / 1000L,
                    )
                )
                inserted++
                inBatch++

                if (inBatch >= 400) {
                    batch.commit().await()
                    batch = db.batch()
                    inBatch = 0
                }
            }

            if (inBatch > 0) batch.commit().await()

            try {
                db.collection(FirebaseConfig.COL_AUDIT_LOG).add(
                    mapOf(
                        "adminUid" to adminUid,
                        "action" to "fb_bulk_import",
                        "targetUid" to "",
                        "amount" to 0L,
                        "reason" to "inserted=$inserted skipped=$skipped",
                        "ts" to System.currentTimeMillis() / 1000L,
                    )
                ).await()
            } catch (_: Exception) {}

            Result.success(inserted)
        } catch (e: Exception) {
            Log.e(TAG, "insertValidated failed", e)
            Result.failure(e)
        }
    }

    // ============================================================
    //  EXPORTS
    // ============================================================
    suspend fun exportJson(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val snap = db.collection(FirebaseConfig.COL_FIREBASES).orderBy("addedAt").get().await()
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

    suspend fun exportCsv(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val snap = db.collection(FirebaseConfig.COL_FIREBASES).orderBy("addedAt").get().await()
            val csv = buildString {
                appendLine("index,id,url,label,added_at")
                snap.documents.forEachIndexed { i, d ->
                    val url = d.getString("url") ?: ""
                    val label = (d.getString("label") ?: "").replace(",", " ")
                    val addedAt = d.getLong("addedAt") ?: 0L
                    appendLine("${i + 1},${d.id},$url,$label,$addedAt")
                }
            }
            Result.success(csv)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportTxtUrlsOnly(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val snap = db.collection(FirebaseConfig.COL_FIREBASES).orderBy("addedAt").get().await()
            val txt = snap.documents.joinToString("\n") { it.getString("url") ?: "" }
            Result.success(txt)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============================================================
    //  VALIDATE SINGLE URL
    // ============================================================
    private suspend fun checkFirebaseUrl(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim().trimEnd('/')
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
                m.contains("unable to resolve") -> "network"
                m.contains("failed to connect") -> "network"
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

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    companion object {
        private const val TAG = "FirebaseImportService"
        private val HEX = "0123456789abcdef".toCharArray()
    }
}