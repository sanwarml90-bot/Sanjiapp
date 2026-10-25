package com.sanji.bomberpro.ui.admin

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.services.FirebaseImportService
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

private enum class BulkTab { FILE, PASTE, EXPORT }
private enum class BulkPhase { IDLE, EXTRACTING, PREVIEW, VALIDATING, INSERTING, RESULT }

// Hard limits — prevent OOM, keep UI responsive
private const val MAX_FILE_BYTES = 10L * 1024L * 1024L          // 10 MB per file
private const val MAX_TOTAL_BYTES = 30L * 1024L * 1024L          // 30 MB aggregate
private const val MAX_FILES = 10

@Composable
fun FirebaseBulkAddScreen(
    adminUid: String,
    onBack: () -> Unit,
    onCompleted: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val service = remember { FirebaseImportService() }

    var tab by remember { mutableStateOf(BulkTab.FILE) }
    var phase by remember { mutableStateOf(BulkPhase.IDLE) }

    val extracted = remember { mutableStateListOf<String>() }
    val selected = remember { mutableStateListOf<Boolean>() }

    var pasteText by remember { mutableStateOf("") }
    var showPasteDialog by remember { mutableStateOf(false) }

    var valCurrent by remember { mutableStateOf(0) }
    var valTotal by remember { mutableStateOf(0) }
    var valValid by remember { mutableStateOf(0) }
    var valFailed by remember { mutableStateOf(0) }
    var phaseLabel by remember { mutableStateOf("") }

    var addedCount by remember { mutableStateOf(0) }
    var skippedExisting by remember { mutableStateOf(0) }
    var failureList by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var fileErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    var showFailureDetails by remember { mutableStateOf(false) }
    var cancelled by remember { mutableStateOf(false) }

    var exportStatus by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val cancelFlag = remember { AtomicBoolean(false) }

    // ============================================================
    //  SAFE FILE READER — byte-capped, OOM-safe
    // ============================================================
    suspend fun readUriSafely(uri: Uri, maxBytes: Long): Result<String> =
        withContext(Dispatchers.IO) {
            var stream: InputStream? = null
            try {
                // Pre-check size via AssetFileDescriptor when available
                try {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                        val declared = afd.length
                        if (declared > maxBytes) {
                            return@withContext Result.failure(
                                Exception("File exceeds ${maxBytes / 1024 / 1024}MB (declared ${declared / 1024 / 1024}MB)")
                            )
                        }
                    }
                } catch (_: Exception) {
                    // AssetFileDescriptor not available — fall through to capped streaming read
                }

                stream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext Result.failure(Exception("Cannot open file"))

                val buf = ByteArray(64 * 1024)
                val out = ByteArrayOutputStream()
                var total = 0L
                while (true) {
                    val n = stream.read(buf)
                    if (n <= 0) break
                    total += n
                    if (total > maxBytes) {
                        return@withContext Result.failure(
                            Exception("File exceeds ${maxBytes / 1024 / 1024}MB while reading")
                        )
                    }
                    out.write(buf, 0, n)
                }
                Result.success(out.toByteArray().toString(Charsets.UTF_8))
            } catch (e: OutOfMemoryError) {
                Result.failure(Exception("Out of memory reading file — try a smaller file"))
            } catch (e: SecurityException) {
                Result.failure(Exception("Permission denied: ${e.message ?: "unknown"}"))
            } catch (e: Exception) {
                Result.failure(Exception("Read failed: ${e.message ?: "unknown"}"))
            } finally {
                try { stream?.close() } catch (_: Exception) {}
            }
        }

    // ============================================================
    //  FILE PICKER — OpenMultipleDocuments, text filters
    // ============================================================
    val multiFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        // Catch Throwable — never let the callback crash the process
        try {
            if (uris.isEmpty()) return@rememberLauncherForActivityResult

            phase = BulkPhase.EXTRACTING
            error = null
            fileErrors = emptyList()
            extracted.clear()
            selected.clear()

            scope.launch {
                try {
                    val allUrls = mutableListOf<String>()
                    val localFileErrors = mutableListOf<String>()
                    var totalBytesRead = 0L
                    var processedCount = 0

                    for (u in uris) {
                        if (processedCount >= MAX_FILES) {
                            localFileErrors.add("Skipped: max $MAX_FILES files per import")
                            break
                        }
                        if (totalBytesRead >= MAX_TOTAL_BYTES) {
                            localFileErrors.add("Skipped: total ${MAX_TOTAL_BYTES / 1024 / 1024}MB limit reached")
                            break
                        }
                        val remaining = MAX_TOTAL_BYTES - totalBytesRead
                        val perFileCap = minOf(MAX_FILE_BYTES, remaining)

                        val fileName = queryDisplayName(context, u) ?: "file"
                        val read = readUriSafely(u, perFileCap)
                        if (read.isFailure) {
                            localFileErrors.add("$fileName: ${read.exceptionOrNull()?.message ?: "read failed"}")
                            continue
                        }
                        val text = read.getOrNull() ?: ""
                        totalBytesRead += text.length.toLong()
                        processedCount++

                        if (text.isBlank()) {
                            localFileErrors.add("$fileName: empty")
                            continue
                        }
                        try {
                            allUrls.addAll(service.extractUrls(text))
                        } catch (e: Exception) {
                            localFileErrors.add("$fileName: extraction error (${e.message ?: "unknown"})")
                        }
                    }

                    val unique = allUrls.toSet().toList()
                    fileErrors = localFileErrors

                    if (unique.isEmpty()) {
                        error = if (localFileErrors.isNotEmpty())
                            "No URLs found. ${localFileErrors.size} file(s) skipped."
                        else
                            "No URLs found in selected file(s)"
                        phase = BulkPhase.IDLE
                        return@launch
                    }
                    extracted.addAll(unique)
                    selected.addAll(unique.map { true })
                    phase = BulkPhase.PREVIEW
                } catch (e: OutOfMemoryError) {
                    error = "Out of memory reading files — try smaller files"
                    phase = BulkPhase.IDLE
                } catch (e: Throwable) {
                    error = "Extract failed: ${e.message ?: e.javaClass.simpleName}"
                    phase = BulkPhase.IDLE
                }
            }
        } catch (e: Throwable) {
            // Absolute last-resort guard — never crash the app from the picker callback
            try {
                error = "File picker callback error: ${e.message ?: e.javaClass.simpleName}"
                phase = BulkPhase.IDLE
            } catch (_: Throwable) {}
        }
    }

    fun runValidateAndInsert() {
        val urls = extracted.filterIndexed { i, _ -> selected.getOrElse(i) { false } }
        if (urls.isEmpty()) {
            error = "Select at least one URL"
            return
        }
        cancelFlag.set(false)
        phase = BulkPhase.VALIDATING
        valCurrent = 0
        valTotal = 0
        valValid = 0
        valFailed = 0
        phaseLabel = "Loading existing…"

        scope.launch {
            try {
                val existing = service.loadExistingUrls()
                val already = urls.count { it.trimEnd('/').lowercase() in existing }
                skippedExisting = already
                val toCheck = urls.filter { it.trimEnd('/').lowercase() !in existing }

                if (toCheck.isEmpty()) {
                    addedCount = 0
                    failureList = emptyList()
                    phase = BulkPhase.RESULT
                    return@launch
                }

                phaseLabel = "Validating…"
                val validation = service.validateUrls(
                    urls = toCheck,
                    existingUrls = existing,
                    cancel = cancelFlag,
                    onProgress = { c, t, v, f ->
                        valCurrent = c
                        valTotal = t
                        valValid = v
                        valFailed = f
                    }
                )

                if (validation.cancelled) {
                    cancelled = true
                    addedCount = 0
                    failureList = validation.failed
                    phase = BulkPhase.RESULT
                    return@launch
                }

                phaseLabel = "Inserting…"
                phase = BulkPhase.INSERTING
                val insertRes = service.insertValidated(adminUid, validation.valid)
                if (insertRes.isFailure) {
                    error = insertRes.exceptionOrNull()?.message ?: "Insert failed"
                    phase = BulkPhase.IDLE
                    return@launch
                }
                addedCount = insertRes.getOrNull() ?: 0
                failureList = validation.failed
                phase = BulkPhase.RESULT
                onCompleted()
            } catch (e: Throwable) {
                error = "Validation failed: ${e.message ?: e.javaClass.simpleName}"
                phase = BulkPhase.IDLE
            }
        }
    }

    fun saveToDownloads(content: String, filename: String, mime: String) {
        scope.launch {
            exportStatus = null
            error = null
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: throw Exception("MediaStore insert failed")
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(content.toByteArray())
                }
                exportStatus = "Saved to Downloads/$filename"
            } catch (e: Exception) {
                try {
                    val file = File(context.filesDir, filename)
                    file.writeText(content)
                    exportStatus = "Saved to app storage/$filename"
                } catch (_: Exception) {
                    error = "Export failed: ${e.message}"
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (phase == BulkPhase.VALIDATING || phase == BulkPhase.INSERTING) {
                        cancelFlag.set(true)
                    } else if (phase == BulkPhase.PREVIEW || phase == BulkPhase.RESULT) {
                        extracted.clear()
                        selected.clear()
                        phase = BulkPhase.IDLE
                        failureList = emptyList()
                        fileErrors = emptyList()
                        addedCount = 0
                        skippedExisting = 0
                        cancelled = false
                        error = null
                    } else {
                        onBack()
                    }
                }) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
                }
                Text(
                    "Bulk Firebases",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = colors.onBackground, modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(8.dp))

            if (phase == BulkPhase.IDLE) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TabChip("File", tab == BulkTab.FILE) { tab = BulkTab.FILE }
                    TabChip("Paste", tab == BulkTab.PASTE) { tab = BulkTab.PASTE }
                    TabChip("Export", tab == BulkTab.EXPORT) { tab = BulkTab.EXPORT }
                }
                Spacer(Modifier.height(12.dp))
            }

            when (phase) {
                BulkPhase.IDLE -> {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    ) {
                        when (tab) {
                            BulkTab.FILE -> {
                                Surface(
                                    onClick = {
                                        try {
                                            multiFilePicker.launch(
                                                arrayOf(
                                                    "text/*",
                                                    "application/json",
                                                    "application/x-json",
                                                    "application/x-ndjson",
                                                )
                                            )
                                        } catch (e: Throwable) {
                                            error = "File picker unavailable: ${e.message ?: e.javaClass.simpleName}"
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = colors.surface,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Default.UploadFile, null, tint = colors.primary, modifier = Modifier.size(28.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Select Files", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                                            Text(
                                                "Text or JSON · up to $MAX_FILES files · ${MAX_FILE_BYTES / 1024 / 1024}MB each",
                                                fontSize = 11.sp, color = colors.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                            BulkTab.PASTE -> {
                                Surface(
                                    onClick = { showPasteDialog = true },
                                    shape = RoundedCornerShape(14.dp),
                                    color = colors.surface,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Default.ContentPaste, null, tint = colors.primary, modifier = Modifier.size(28.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Paste URLs", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                                            Text("Paste many URLs at once", fontSize = 11.sp, color = colors.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            BulkTab.EXPORT -> {
                                ExportButton("JSON (full)", "Full database backup", onExport = {
                                    scope.launch {
                                        val r = service.exportJson()
                                        if (r.isSuccess) {
                                            saveToDownloads(
                                                r.getOrNull() ?: "",
                                                "firebases_${System.currentTimeMillis()}.json",
                                                "application/json",
                                            )
                                        } else error = r.exceptionOrNull()?.message
                                    }
                                })
                                Spacer(Modifier.height(8.dp))
                                ExportButton("CSV (spreadsheet)", "Open in Excel / Sheets", onExport = {
                                    scope.launch {
                                        val r = service.exportCsv()
                                        if (r.isSuccess) {
                                            saveToDownloads(
                                                r.getOrNull() ?: "",
                                                "firebases_${System.currentTimeMillis()}.csv",
                                                "text/csv",
                                            )
                                        } else error = r.exceptionOrNull()?.message
                                    }
                                })
                                Spacer(Modifier.height(8.dp))
                                ExportButton("TXT (URLs only)", "One URL per line", onExport = {
                                    scope.launch {
                                        val r = service.exportTxtUrlsOnly()
                                        if (r.isSuccess) {
                                            saveToDownloads(
                                                r.getOrNull() ?: "",
                                                "firebases_urls_${System.currentTimeMillis()}.txt",
                                                "text/plain",
                                            )
                                        } else error = r.exceptionOrNull()?.message
                                    }
                                })
                                if (exportStatus != null) {
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        exportStatus!!,
                                        fontSize = 12.sp, color = colors.success,
                                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }

                        if (fileErrors.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colors.error.copy(alpha = 0.1f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        "Skipped files (${fileErrors.size}):",
                                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                        color = colors.error,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    fileErrors.take(8).forEach { e ->
                                        Text("• $e", fontSize = 10.sp, color = colors.onBackground)
                                    }
                                    if (fileErrors.size > 8) {
                                        Text("+${fileErrors.size - 8} more", fontSize = 10.sp, color = colors.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        if (error != null) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                error!!,
                                color = colors.error,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }

                BulkPhase.EXTRACTING -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = colors.primary)
                            Spacer(Modifier.height(12.dp))
                            Text("Extracting URLs…", color = colors.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }

                BulkPhase.PREVIEW -> PreviewPhase(
                    extracted = extracted,
                    selected = selected,
                    onToggle = { i -> if (i in selected.indices) selected[i] = !selected[i] },
                    onSelectAll = { for (i in selected.indices) selected[i] = true },
                    onDeselectAll = { for (i in selected.indices) selected[i] = false },
                    onProceed = { runValidateAndInsert() },
                    onCancel = {
                        extracted.clear()
                        selected.clear()
                        fileErrors = emptyList()
                        phase = BulkPhase.IDLE
                        error = null
                    },
                )

                BulkPhase.VALIDATING, BulkPhase.INSERTING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = colors.primary)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            phaseLabel, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold, color = colors.onBackground,
                        )
                        if (valTotal > 0) {
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { if (valTotal > 0) valCurrent.toFloat() / valTotal else 0f },
                                color = colors.primary,
                                trackColor = colors.surface,
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "$valCurrent / $valTotal  ·  ✅ $valValid  ·  ❌ $valFailed",
                                fontSize = 11.sp, color = colors.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = { cancelFlag.set(true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.error, contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("Cancel", fontWeight = FontWeight.Bold) }
                    }
                }

                BulkPhase.RESULT -> ResultPhase(
                    added = addedCount,
                    skippedExisting = skippedExisting,
                    failureCount = failureList.size,
                    failureList = failureList,
                    cancelled = cancelled,
                    showDetails = showFailureDetails,
                    onToggleDetails = { showFailureDetails = !showFailureDetails },
                    onDone = {
                        extracted.clear()
                        selected.clear()
                        fileErrors = emptyList()
                        phase = BulkPhase.IDLE
                        failureList = emptyList()
                        addedCount = 0
                        skippedExisting = 0
                        cancelled = false
                        error = null
                    },
                    onBack = onBack,
                )
            }
        }
    }

    if (showPasteDialog) {
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val txt = pasteText.trim()
                        if (txt.isBlank()) return@TextButton
                        showPasteDialog = false
                        scope.launch {
                            phase = BulkPhase.EXTRACTING
                            error = null
                            fileErrors = emptyList()
                            extracted.clear()
                            selected.clear()
                            try {
                                val urls = service.extractUrls(txt)
                                if (urls.isEmpty()) {
                                    error = "No URLs found in pasted text"
                                    phase = BulkPhase.IDLE
                                    return@launch
                                }
                                extracted.addAll(urls)
                                selected.addAll(urls.map { true })
                                phase = BulkPhase.PREVIEW
                            } catch (e: Throwable) {
                                error = "Extract failed: ${e.message ?: e.javaClass.simpleName}"
                                phase = BulkPhase.IDLE
                            }
                        }
                        pasteText = ""
                    },
                    enabled = pasteText.isNotBlank(),
                ) { Text("Extract", color = colors.primary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) {
                    Text("Cancel", color = colors.onSurfaceVariant)
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.onBackground,
            textContentColor = colors.onBackground,
            title = { Text("Paste URLs") },
            text = {
                Column {
                    Text(
                        "Paste any text with URLs — one per line or mixed.",
                        fontSize = 11.sp, color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pasteText,
                        onValueChange = { pasteText = it },
                        placeholder = { Text("https://example.firebaseio.com\nhttps://other.firebasedatabase.app") },
                        minLines = 6,
                        maxLines = 12,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${pasteText.length} chars",
                        fontSize = 10.sp, color = colors.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

// ============================================================
//  HELPERS
// ============================================================
private fun queryDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalSanjiColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) colors.primary else colors.surface,
    ) {
        Text(
            label,
            fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = if (selected) Color.Black else colors.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ExportButton(
    title: String,
    subtitle: String,
    onExport: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    Surface(
        onClick = onExport,
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Download, null, tint = colors.success, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                Text(subtitle, fontSize = 11.sp, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PreviewPhase(
    extracted: List<String>,
    selected: List<Boolean>,
    onToggle: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onProceed: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val selectedCount = selected.count { it }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${extracted.size} URLs found · $selectedCount selected",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = colors.onBackground, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSelectAll) { Text("All", color = colors.primary, fontSize = 11.sp) }
            TextButton(onClick = onDeselectAll) { Text("None", color = colors.onSurfaceVariant, fontSize = 11.sp) }
        }

        Spacer(Modifier.height(6.dp))

        Surface(shape = RoundedCornerShape(12.dp), color = colors.surface, modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                items(extracted.size) { i ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selected.getOrElse(i) { true },
                            onCheckedChange = { onToggle(i) },
                            colors = CheckboxDefaults.colors(checkedColor = colors.primary),
                        )
                        Text(
                            extracted.getOrNull(i) ?: "",
                            fontSize = 11.sp, color = colors.onBackground,
                            fontFamily = FontFamily.Monospace, maxLines = 2,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.surface,
                    contentColor = colors.onBackground,
                ),
            ) { Text("Cancel") }
            Button(
                onClick = onProceed,
                enabled = selectedCount > 0,
                modifier = Modifier.weight(2f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = Color.Black,
                ),
            ) { Text("Validate & Add ($selectedCount)", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ResultPhase(
    added: Int,
    skippedExisting: Int,
    failureCount: Int,
    failureList: List<Pair<String, String>>,
    cancelled: Boolean,
    showDetails: Boolean,
    onToggleDetails: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(
            if (added > 0) Icons.Default.CheckCircle else Icons.Default.Error,
            null,
            tint = if (added > 0) colors.success else colors.warning,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (cancelled) "Cancelled" else "Import Complete",
            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.onBackground,
        )
        Spacer(Modifier.height(20.dp))

        Surface(shape = RoundedCornerShape(14.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                ResultRow("✅ Added", "$added", colors.success)
                ResultRow("⏭ Already existed", "$skippedExisting")
                ResultRow("❌ Failed", "$failureCount", if (failureCount > 0) colors.error else colors.onBackground)
            }
        }

        if (failureList.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Surface(
                onClick = onToggleDetails,
                shape = RoundedCornerShape(12.dp),
                color = colors.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Show failure details (${failureList.size})",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = colors.onBackground, modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null, tint = colors.primary, modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (showDetails) {
                Spacer(Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        failureList.take(50).forEach { (url, reason) ->
                            Text("❌ $reason", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.error)
                            Text(url, fontSize = 10.sp, color = colors.onSurfaceVariant, fontFamily = FontFamily.Monospace, maxLines = 1)
                        }
                        if (failureList.size > 50) {
                            Text("+${failureList.size - 50} more", fontSize = 10.sp, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary, contentColor = Color.Black,
            ),
        ) { Text("Import More", fontWeight = FontWeight.Bold) }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back to Admin", color = colors.onSurfaceVariant)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ResultRow(label: String, value: String, valueColor: Color? = null) {
    val colors = LocalSanjiColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 12.sp, color = colors.onSurfaceVariant)
        Text(
            value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = valueColor ?: colors.onBackground,
        )
    }
}