package com.sanji.bomberpro.ui.admin

import android.content.ContentValues
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.services.FirebaseImportService
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private enum class BulkTab { FILE, PASTE, EXPORT }
private enum class BulkPhase { IDLE, EXTRACTING, PREVIEW, VALIDATING, INSERTING, RESULT }

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

    var advancedOpen by remember { mutableStateOf(false) }
    var useCustomRegex by remember { mutableStateOf(false) }
    var customRegex by remember { mutableStateOf("") }
    var excludeExisting by remember { mutableStateOf(true) }

    var valCurrent by remember { mutableStateOf(0) }
    var valTotal by remember { mutableStateOf(0) }
    var valValid by remember { mutableStateOf(0) }
    var valFailed by remember { mutableStateOf(0) }
    var phaseLabel by remember { mutableStateOf("") }

    var addedCount by remember { mutableStateOf(0) }
    var skippedExisting by remember { mutableStateOf(0) }
    var failureList by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var showFailureDetails by remember { mutableStateOf(false) }
    var cancelled by remember { mutableStateOf(false) }

    var exportStatus by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val cancelFlag = remember { AtomicBoolean(false) }

    val multiFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            phase = BulkPhase.EXTRACTING
            error = null
            extracted.clear()
            selected.clear()
            try {
                val pattern = if (useCustomRegex && customRegex.isNotBlank()) customRegex else null
                val allUrls = mutableListOf<String>()
                for (u in uris) {
                    val read = service.readUriText(u, context.contentResolver)
                    if (read.isFailure) {
                        error = "Read failed for one file: ${read.exceptionOrNull()?.message}"
                        continue
                    }
                    val urls = service.extractUrls(read.getOrNull() ?: "", pattern)
                    allUrls.addAll(urls)
                }
                val unique = allUrls.toSet().toList()
                if (unique.isEmpty()) {
                    error = "No URLs found in selected file(s)"
                    phase = BulkPhase.IDLE
                    return@launch
                }
                extracted.addAll(unique)
                selected.addAll(unique.map { true })
                phase = BulkPhase.PREVIEW
            } catch (e: Exception) {
                error = "Extract failed: ${e.message}"
                phase = BulkPhase.IDLE
            }
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
                val existing = if (excludeExisting) service.loadExistingUrls() else emptySet()
                val already = urls.count { it.trimEnd('/') in existing }
                skippedExisting = already
                val toCheck = urls.filter { it.trimEnd('/') !in existing }

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
            } catch (e: Exception) {
                error = "Validation failed: ${e.message}"
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
                            BulkTab.FILE -> FileTab(
                                onPick = {
                                    try {
                                        multiFilePicker.launch(arrayOf("*/*"))
                                    } catch (e: Exception) {
                                        error = "File picker not available — use Paste instead (${e.message})"
                                    }
                                },
                                advancedOpen = advancedOpen,
                                onAdvancedToggle = { advancedOpen = !advancedOpen },
                                useCustomRegex = useCustomRegex,
                                onUseCustomRegexToggle = { useCustomRegex = !useCustomRegex },
                                customRegex = customRegex,
                                onCustomRegexChange = { customRegex = it },
                                excludeExisting = excludeExisting,
                                onExcludeExistingToggle = { excludeExisting = !excludeExisting },
                            )
                            BulkTab.PASTE -> PasteTab(
                                onOpenDialog = { showPasteDialog = true },
                                advancedOpen = advancedOpen,
                                onAdvancedToggle = { advancedOpen = !advancedOpen },
                                useCustomRegex = useCustomRegex,
                                onUseCustomRegexToggle = { useCustomRegex = !useCustomRegex },
                                customRegex = customRegex,
                                onCustomRegexChange = { customRegex = it },
                            )
                            BulkTab.EXPORT -> ExportTab(
                                onExportJson = {
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
                                },
                                onExportCsv = {
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
                                },
                                onExportTxt = {
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
                                },
                                exportStatus = exportStatus,
                            )
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

                BulkPhase.PREVIEW -> {
                    PreviewPhase(
                        extracted = extracted,
                        selected = selected,
                        onToggle = { i ->
                            if (i in selected.indices) selected[i] = !selected[i]
                        },
                        onSelectAll = { for (i in selected.indices) selected[i] = true },
                        onDeselectAll = { for (i in selected.indices) selected[i] = false },
                        onProceed = { runValidateAndInsert() },
                        onCancel = {
                            extracted.clear()
                            selected.clear()
                            phase = BulkPhase.IDLE
                            error = null
                        },
                    )
                }

                BulkPhase.VALIDATING, BulkPhase.INSERTING -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator(color = colors.primary)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                phaseLabel,
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                color = colors.onBackground,
                            )
                            if (valTotal > 0) {
                                Spacer(Modifier.height(12.dp))
                                LinearProgressIndicator(
                                    progress = {
                                        if (valTotal > 0) valCurrent.toFloat() / valTotal else 0f
                                    },
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
                }

                BulkPhase.RESULT -> {
                    ResultPhase(
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
                            extracted.clear()
                            selected.clear()
                            try {
                                val pattern = if (useCustomRegex && customRegex.isNotBlank()) customRegex else null
                                val urls = service.extractUrls(txt, pattern)
                                if (urls.isEmpty()) {
                                    error = "No URLs found in pasted text"
                                    phase = BulkPhase.IDLE
                                    return@launch
                                }
                                extracted.addAll(urls)
                                selected.addAll(urls.map { true })
                                phase = BulkPhase.PREVIEW
                            } catch (e: Exception) {
                                error = "Extract failed: ${e.message}"
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
private fun FileTab(
    onPick: () -> Unit,
    advancedOpen: Boolean,
    onAdvancedToggle: () -> Unit,
    useCustomRegex: Boolean,
    onUseCustomRegexToggle: () -> Unit,
    customRegex: String,
    onCustomRegexChange: (String) -> Unit,
    excludeExisting: Boolean,
    onExcludeExistingToggle: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    Surface(
        onClick = onPick,
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
                Text("Pick one or more text files", fontSize = 11.sp, color = colors.onSurfaceVariant)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    AdvancedSection(
        expanded = advancedOpen,
        onToggle = onAdvancedToggle,
        useCustomRegex = useCustomRegex,
        onUseCustomRegexToggle = onUseCustomRegexToggle,
        customRegex = customRegex,
        onCustomRegexChange = onCustomRegexChange,
        excludeExisting = excludeExisting,
        onExcludeExistingToggle = onExcludeExistingToggle,
    )
}

@Composable
private fun PasteTab(
    onOpenDialog: () -> Unit,
    advancedOpen: Boolean,
    onAdvancedToggle: () -> Unit,
    useCustomRegex: Boolean,
    onUseCustomRegexToggle: () -> Unit,
    customRegex: String,
    onCustomRegexChange: (String) -> Unit,
) {
    val colors = LocalSanjiColors.current
    Surface(
        onClick = onOpenDialog,
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
                Text("Paste Text", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                Text("Open paste dialog", fontSize = 11.sp, color = colors.onSurfaceVariant)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    AdvancedSection(
        expanded = advancedOpen,
        onToggle = onAdvancedToggle,
        useCustomRegex = useCustomRegex,
        onUseCustomRegexToggle = onUseCustomRegexToggle,
        customRegex = customRegex,
        onCustomRegexChange = onCustomRegexChange,
        excludeExisting = true,
        onExcludeExistingToggle = {},
    )
}

@Composable
private fun AdvancedSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    useCustomRegex: Boolean,
    onUseCustomRegexToggle: () -> Unit,
    customRegex: String,
    onCustomRegexChange: (String) -> Unit,
    excludeExisting: Boolean,
    onExcludeExistingToggle: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Advanced",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = colors.onBackground, modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null, tint = colors.primary, modifier = Modifier.size(20.dp),
            )
        }
    }

    if (expanded) {
        Spacer(Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(12.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Skip already existing", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                        Text("Don't re-add URLs already in the list", fontSize = 10.sp, color = colors.onSurfaceVariant)
                    }
                    Switch(
                        checked = excludeExisting,
                        onCheckedChange = { onExcludeExistingToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.primary,
                            checkedTrackColor = colors.primary.copy(alpha = 0.4f),
                        ),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Custom regex", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                        Text("Override URL extraction pattern", fontSize = 10.sp, color = colors.onSurfaceVariant)
                    }
                    Switch(
                        checked = useCustomRegex,
                        onCheckedChange = { onUseCustomRegexToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.primary,
                            checkedTrackColor = colors.primary.copy(alpha = 0.4f),
                        ),
                    )
                }
                if (useCustomRegex) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customRegex,
                        onValueChange = onCustomRegexChange,
                        label = { Text("Regex") },
                        placeholder = { Text("""https?://[^\s]+""") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportTab(
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onExportTxt: () -> Unit,
    exportStatus: String?,
) {
    val colors = LocalSanjiColors.current
    ExportButton("JSON (full)", "Full database backup", Icons.Default.Download, onExportJson)
    Spacer(Modifier.height(8.dp))
    ExportButton("CSV (spreadsheet)", "Open in Excel / Sheets", Icons.Default.Download, onExportCsv)
    Spacer(Modifier.height(8.dp))
    ExportButton("TXT (URLs only)", "One URL per line", Icons.Default.Download, onExportTxt)

    if (exportStatus != null) {
        Spacer(Modifier.height(16.dp))
        Text(
            exportStatus,
            fontSize = 12.sp, color = colors.success,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ExportButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = colors.success, modifier = Modifier.size(24.dp))
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
                            Text(
                                "❌ $reason",
                                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.error,
                            )
                            Text(
                                url,
                                fontSize = 10.sp, color = colors.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace, maxLines = 1,
                            )
                        }
                        if (failureList.size > 50) {
                            Text(
                                "+${failureList.size - 50} more",
                                fontSize = 10.sp, color = colors.onSurfaceVariant,
                            )
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