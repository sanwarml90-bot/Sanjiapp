package com.sanji.bomberpro.ui.admin

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.sanji.bomberpro.services.ImportResult
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch
import java.io.File

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

    var showPasteDialog by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }

    var processing by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf("") }
    var progressCurrent by remember { mutableStateOf(0) }
    var progressTotal by remember { mutableStateOf(0) }
    var progressAdded by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<ImportResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun runImport(block: suspend () -> Result<ImportResult>) {
        processing = true
        phase = "starting"
        progressCurrent = 0
        progressTotal = 0
        progressAdded = 0
        error = null
        result = null
        try {
            val res = block()
            if (res.isSuccess) {
                result = res.getOrNull()
                onCompleted()
            } else {
                error = res.exceptionOrNull()?.message ?: "Import failed"
            }
        } catch (e: Exception) {
            error = e.message ?: "Import failed"
        } finally {
            processing = false
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runImport {
                    service.importFromUri(
                        adminUid = adminUid,
                        uri = uri,
                        contentResolver = context.contentResolver,
                        onProgress = { p, c, t, a ->
                            phase = p
                            progressCurrent = c
                            progressTotal = t
                            progressAdded = a
                        }
                    )
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (!processing) onBack()
                }) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
                }
                Text(
                    "Bulk Add Firebases",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = colors.onBackground, modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Import firebases from a file or paste URLs in bulk.",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Supports: .txt, .json, .csv, .html, .log — any text containing URLs",
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                if (!processing && result == null) {
                    // file upload card
                    Surface(
                        onClick = { filePicker.launch("*/*") },
                        shape = RoundedCornerShape(14.dp),
                        color = colors.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.UploadFile, null,
                                tint = colors.primary,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Upload File",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onBackground,
                                )
                                Text(
                                    "Pick any text file containing URLs",
                                    fontSize = 11.sp,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // paste card
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
                            Icon(
                                Icons.Default.ContentPaste, null,
                                tint = colors.primary,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Paste URLs",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onBackground,
                                )
                                Text(
                                    "Paste bulk text with URLs",
                                    fontSize = 11.sp,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // export card
                    Surface(
                        onClick = {
                            scope.launch {
                                val res = service.exportFirebases()
                                if (res.isSuccess) {
                                    val json = res.getOrNull() ?: ""
                                    val filename = "firebases_export_${System.currentTimeMillis()}.json"
                                    val file = File(context.cacheDir, filename)
                                    file.writeText(json)
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file,
                                        ))
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(share, "Export firebases"))
                                } else {
                                    error = res.exceptionOrNull()?.message
                                }
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
                            Icon(
                                Icons.Default.Download, null,
                                tint = colors.success,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Export Firebases",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onBackground,
                                )
                                Text(
                                    "Download all firebases as JSON",
                                    fontSize = 11.sp,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // progress
                if (processing) {
                    Spacer(Modifier.height(20.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                when (phase) {
                                    "extracting" -> "Extracting URLs…"
                                    "loading" -> "Loading existing firebases…"
                                    "validating" -> "Validating URLs…"
                                    "inserting" -> "Saving valid URLs…"
                                    "done" -> "Done"
                                    else -> "Processing…"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onBackground,
                            )
                            if (progressTotal > 0) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = {
                                        if (progressTotal > 0)
                                            progressCurrent.toFloat() / progressTotal
                                        else 0f
                                    },
                                    color = colors.primary,
                                    trackColor = colors.background,
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "$progressCurrent / $progressTotal  ·  ✅ $progressAdded",
                                    fontSize = 11.sp,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // result
                if (result != null) {
                    val r = result!!
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (r.added > 0) Icons.Default.CheckCircle else Icons.Default.Error,
                                    null,
                                    tint = if (r.added > 0) colors.success else colors.warning,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Import Complete",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onBackground,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            ResultRow("Total URLs detected", "${r.totalUrls}")
                            ResultRow("✅ Added", "${r.added}", colors.success)
                            ResultRow("⏭ Already exists", "${r.skippedExisting}")
                            ResultRow("⏭ Duplicate in batch", "${r.skippedDuplicate}")
                            ResultRow("❌ Failed", "${r.failed}", if (r.failed > 0) colors.error else colors.onBackground)
                            ResultRow("🔥 Total now", "${r.existingAfter}", colors.primary)

                            if (r.failureSummary.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "Failure breakdown:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onBackground,
                                )
                                Text(
                                    r.failureSummary,
                                    fontSize = 11.sp,
                                    color = colors.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            result = null
                            error = null
                            progressCurrent = 0
                            progressTotal = 0
                            progressAdded = 0
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = Color.Black,
                        ),
                    ) { Text("Import More", fontWeight = FontWeight.Bold) }

                    Spacer(Modifier.height(8.dp))

                    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("Back to Admin", color = colors.onSurfaceVariant)
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
    }

    // paste dialog
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
                            runImport {
                                service.importFromText(
                                    adminUid = adminUid,
                                    text = txt,
                                    onProgress = { p, c, t, a ->
                                        phase = p
                                        progressCurrent = c
                                        progressTotal = t
                                        progressAdded = a
                                    }
                                )
                            }
                        }
                        pasteText = ""
                    },
                    enabled = pasteText.isNotBlank(),
                ) { Text("Import", color = colors.primary, fontWeight = FontWeight.Bold) }
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
                        "Paste any text containing URLs — one per line, or mixed.",
                        fontSize = 11.sp,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pasteText,
                        onValueChange = { pasteText = it },
                        placeholder = { Text("https://example.firebaseio.com\nhttps://other.firebasedatabase.app\n…") },
                        minLines = 6,
                        maxLines = 12,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${pasteText.length} chars",
                        fontSize = 10.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
            },
        )
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
            value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor ?: colors.onBackground,
        )
    }
}