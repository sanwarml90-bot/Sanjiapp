package com.sanji.bomberpro.ui.admin

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.data.ForceJoinChannel
import com.sanji.bomberpro.data.ForceJoinState
import com.sanji.bomberpro.services.ForceJoinService
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch

@Composable
fun ForceJoinAdminScreen(
    adminUid: String,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val service = remember { ForceJoinService() }

    var state by remember { mutableStateOf<ForceJoinState?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    var showAddChannel by remember { mutableStateOf(false) }
    var showAddBypass by remember { mutableStateOf(false) }

    suspend fun refresh() {
        loading = true
        try {
            state = service.listAll()
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(error) {
        if (error != null) {
            kotlinx.coroutines.delay(4000)
            error = null
        }
    }
    LaunchedEffect(success) {
        if (success != null) {
            kotlinx.coroutines.delay(4000)
            success = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
                }
                Text(
                    "Force Join",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = colors.onBackground, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { scope.launch { refresh() } }) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = colors.primary)
                }
            }

            Spacer(Modifier.height(16.dp))

            if (loading || state == null) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primary)
                }
                return@Column
            }

            val s = state!!

            // Master toggle
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Force Join Enabled",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = colors.onBackground,
                        )
                        Text(
                            if (s.enabled) "Users must join channels before using the app"
                            else "Force join is off — all users can access the app",
                            fontSize = 11.sp, color = colors.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = s.enabled,
                        onCheckedChange = { checked ->
                            scope.launch {
                                val ok = service.setForceJoinEnabled(checked)
                                if (ok) {
                                    success = if (checked) "Force join enabled" else "Force join disabled"
                                    refresh()
                                } else {
                                    error = "Failed to update"
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.primary,
                            checkedTrackColor = colors.primary.copy(alpha = 0.4f),
                        ),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Channels
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Channels (${s.channels.size})",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = colors.onBackground, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showAddChannel = true }) {
                    Icon(Icons.Default.Add, null, tint = colors.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add", color = colors.primary, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(6.dp))

            if (s.channels.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "No channels added yet.\nTap Add to configure a force-join channel.",
                        fontSize = 12.sp, color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                s.channels.forEach { ch ->
                    ChannelRow(
                        channel = ch,
                        onRemove = {
                            scope.launch {
                                val ok = service.removeChannel(ch.id)
                                if (ok) {
                                    success = "Removed ${ch.title}"
                                    refresh()
                                } else {
                                    error = "Failed to remove"
                                }
                            }
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            // Bypass list
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Bypass (${s.bypass.size})",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = colors.onBackground, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showAddBypass = true }) {
                    Icon(Icons.Default.Add, null, tint = colors.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add", color = colors.primary, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Users in this list skip force-join requirement.",
                fontSize = 11.sp, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))

            if (s.bypass.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "No bypass entries.",
                        fontSize = 12.sp, color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                s.bypass.forEach { uid ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.surface,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                uid,
                                fontSize = 12.sp, color = colors.onBackground,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                scope.launch {
                                    val ok = service.removeBypass(uid)
                                    if (ok) {
                                        success = "Bypass removed"
                                        refresh()
                                    } else {
                                        error = "Failed to remove"
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Delete, "Remove", tint = colors.error)
                            }
                        }
                    }
                }
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    error!!, color = colors.error, fontSize = 12.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
            if (success != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    success!!, color = colors.success, fontSize = 12.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAddChannel) {
        AddChannelDialog(
            onDismiss = { showAddChannel = false },
            onAdd = { id, title, link ->
                scope.launch {
                    val res = service.addChannel(id, title, link, required = true)
                    showAddChannel = false
                    if (res.isSuccess) {
                        success = "Channel added"
                        refresh()
                    } else {
                        error = res.exceptionOrNull()?.message ?: "Add failed"
                    }
                }
            },
        )
    }

    if (showAddBypass) {
        AddBypassDialog(
            onDismiss = { showAddBypass = false },
            onAdd = { uid ->
                scope.launch {
                    val ok = service.addBypass(uid)
                    showAddBypass = false
                    if (ok) {
                        success = "Bypass added"
                        refresh()
                    } else {
                        error = "Add failed"
                    }
                }
            },
        )
    }
}

@Composable
private fun ChannelRow(
    channel: ForceJoinChannel,
    onRemove: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    channel.title.ifBlank { channel.id },
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = colors.onBackground,
                )
                Text(
                    channel.id,
                    fontSize = 10.sp, color = colors.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    channel.link,
                    fontSize = 10.sp, color = colors.primary,
                    fontFamily = FontFamily.Monospace,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, "Remove", tint = colors.error)
            }
        }
    }
}

@Composable
private fun AddChannelDialog(
    onDismiss: () -> Unit,
    onAdd: (id: String, title: String, link: String) -> Unit,
) {
    val colors = LocalSanjiColors.current
    var id by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }

    val canSubmit = id.isNotBlank() && link.isNotBlank() && link.startsWith("http")

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (canSubmit) onAdd(id.trim(), title.trim(), link.trim()) },
                enabled = canSubmit,
            ) { Text("Add", color = colors.primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.onSurfaceVariant)
            }
        },
        containerColor = colors.surface,
        titleContentColor = colors.onBackground,
        textContentColor = colors.onBackground,
        title = { Text("Add Channel") },
        text = {
            Column {
                Text(
                    "Channel ID: @username for public, or -100… for private groups.",
                    fontSize = 11.sp, color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text("Channel ID") },
                    placeholder = { Text("@mychannel or -1001234567890") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text("Display Title (optional)") },
                    placeholder = { Text("My Channel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text("Invite Link") },
                    placeholder = { Text("https://t.me/mychannel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun AddBypassDialog(
    onDismiss: () -> Unit,
    onAdd: (uid: String) -> Unit,
) {
    val colors = LocalSanjiColors.current
    var uid by remember { mutableStateOf("") }

    val canSubmit = uid.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (canSubmit) onAdd(uid.trim()) },
                enabled = canSubmit,
            ) { Text("Add", color = colors.primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.onSurfaceVariant)
            }
        },
        containerColor = colors.surface,
        titleContentColor = colors.onBackground,
        textContentColor = colors.onBackground,
        title = { Text("Add Bypass") },
        text = {
            Column {
                Text(
                    "User UID to bypass force-join requirement.",
                    fontSize = 11.sp, color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = uid,
                    onValueChange = { uid = it },
                    label = { Text("User UID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}