package com.sanji.bomberpro.ui.templates

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlusOne
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.services.MessageTemplate
import com.sanji.bomberpro.services.TemplateService
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch

@Composable
fun TemplatesScreen(
    uid: String,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val service = remember { TemplateService() }

    var templates by remember { mutableStateOf<List<MessageTemplate>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var editingTemplate by remember { mutableStateOf<MessageTemplate?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    suspend fun refresh() {
        loading = true
        templates = service.listTemplates(uid)
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
            }
            Text(
                "Templates",
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = colors.onBackground, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.PlusOne, "New", tint = colors.primary)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
            return
        }

        if (templates.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📝", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No templates yet",
                        fontSize = 14.sp,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Save messages you use often",
                        fontSize = 11.sp,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { showCreate = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = Color.Black,
                        ),
                    ) { Text("Create Template") }
                }
            }
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(templates) { tpl ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                tpl.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onBackground,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { editingTemplate = tpl }) {
                                Icon(Icons.Default.Edit, "Edit", tint = colors.primary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    service.deleteTemplate(uid, tpl.id)
                                    refresh()
                                }
                            }) {
                                Icon(Icons.Default.Delete, "Delete", tint = colors.error, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tpl.text,
                            fontSize = 11.sp,
                            color = colors.onSurfaceVariant,
                            maxLines = 3,
                        )
                    }
                }
            }
        }

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = colors.error, fontSize = 12.sp)
        }
    }

    // create dialog
    if (showCreate) {
        TemplateEditDialog(
            title = "New Template",
            initialName = "",
            initialText = "",
            onDismiss = { showCreate = false },
            onSave = { name, text ->
                scope.launch {
                    val result = service.saveTemplate(uid, name, text)
                    showCreate = false
                    if (result.isSuccess) {
                        refresh()
                    } else {
                        error = result.exceptionOrNull()?.message ?: "Save failed"
                    }
                }
            },
        )
    }

    // edit dialog
    val editing = editingTemplate
    if (editing != null) {
        TemplateEditDialog(
            title = "Edit Template",
            initialName = editing.name,
            initialText = editing.text,
            onDismiss = { editingTemplate = null },
            onSave = { name, text ->
                scope.launch {
                    val result = service.updateTemplate(uid, editing.id, name, text)
                    editingTemplate = null
                    if (result.isSuccess) {
                        refresh()
                    } else {
                        error = result.exceptionOrNull()?.message ?: "Update failed"
                    }
                }
            },
        )
    }
}

@Composable
private fun TemplateEditDialog(
    title: String,
    initialName: String,
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    val colors = LocalSanjiColors.current
    var name by remember { mutableStateOf(initialName) }
    var text by remember { mutableStateOf(initialText) }

    val canSubmit = name.isNotBlank() && text.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (canSubmit) onSave(name.trim(), text.trim()) },
                enabled = canSubmit,
            ) { Text("Save", color = colors.primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colors.onSurfaceVariant) }
        },
        containerColor = colors.surface,
        titleContentColor = colors.onBackground,
        textContentColor = colors.onBackground,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Template name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(1000) },
                    label = { Text("Message text") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${text.length} / 1000",
                    fontSize = 10.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        },
    )
}