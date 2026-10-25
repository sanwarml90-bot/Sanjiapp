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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import com.sanji.bomberpro.services.AppealRecord
import com.sanji.bomberpro.services.AppealService
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FMT = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
private fun fmt(ts: Long): String =
    try { FMT.format(Date(ts * 1000L)) } catch (_: Exception) { "—" }

@Composable
fun AppealsAdminScreen(
    adminUid: String,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val service = remember { AppealService() }

    var appeals by remember { mutableStateOf<List<AppealRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var actionRunning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    var resolveTarget by remember { mutableStateOf<AppealRecord?>(null) }
    var rejectTarget by remember { mutableStateOf<AppealRecord?>(null) }
    var adminReply by remember { mutableStateOf("") }
    var unbanOnResolve by remember { mutableStateOf(true) }

    suspend fun refresh() {
        loading = true
        try {
            appeals = service.listOpenAppeals()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    LaunchedEffect(error) {
        if (error != null) { kotlinx.coroutines.delay(4000); error = null }
    }
    LaunchedEffect(success) {
        if (success != null) { kotlinx.coroutines.delay(3000); success = null }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
                }
                Text(
                    "Appeals (${appeals.size})",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = colors.onBackground, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { scope.launch { refresh() } }) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = colors.primary)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Open ban appeals from banned users. Resolve unbans them automatically.",
                fontSize = 11.sp, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primary)
                }
            } else if (appeals.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📭", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No open appeals", fontSize = 14.sp, color = colors.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(appeals) { a ->
                        AppealCard(
                            appeal = a,
                            onResolve = {
                                adminReply = ""
                                unbanOnResolve = true
                                resolveTarget = a
                            },
                            onReject = {
                                adminReply = ""
                                rejectTarget = a
                            },
                        )
                    }
                }
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error!!, color = colors.error, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            if (success != null) {
                Spacer(Modifier.height(12.dp))
                Text(success!!, color = colors.success, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    // ---- Resolve dialog ----
    val toResolve = resolveTarget
    if (toResolve != null) {
        AlertDialog(
            onDismissRequest = { if (!actionRunning) resolveTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        actionRunning = true
                        scope.launch {
                            val ok = service.resolveAppeal(
                                appealId = toResolve.id,
                                adminUid = adminUid,
                                adminReply = adminReply.trim(),
                                unbanUser = unbanOnResolve,
                            )
                            actionRunning = false
                            resolveTarget = null
                            if (ok) {
                                success = "Resolved" + if (unbanOnResolve) " · user unbanned" else ""
                                refresh()
                            } else {
                                error = "Resolve failed"
                            }
                        }
                    },
                    enabled = !actionRunning,
                ) { Text("RESOLVE", color = colors.success, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { if (!actionRunning) resolveTarget = null }) {
                    Text("Cancel", color = colors.onSurfaceVariant)
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.onBackground,
            textContentColor = colors.onBackground,
            title = { Text("Resolve Appeal?") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "User: ${toResolve.userName.ifBlank { toResolve.uid }}",
                        fontSize = 12.sp, color = colors.onBackground,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Appeal: ${toResolve.text}",
                        fontSize = 11.sp, color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Unban user",
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = colors.onBackground,
                            )
                            Text(
                                "Clears banned status + ban reason",
                                fontSize = 10.sp, color = colors.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = unbanOnResolve,
                            onCheckedChange = { unbanOnResolve = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.success,
                                checkedTrackColor = colors.success.copy(alpha = 0.4f),
                            ),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Admin reply (optional):", fontSize = 11.sp, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = adminReply,
                        onValueChange = { adminReply = it.take(500) },
                        placeholder = { Text("e.g. Understood — account restored.") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }

    // ---- Reject dialog ----
    val toReject = rejectTarget
    if (toReject != null) {
        AlertDialog(
            onDismissRequest = { if (!actionRunning) rejectTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        actionRunning = true
                        scope.launch {
                            val ok = service.rejectAppeal(
                                appealId = toReject.id,
                                adminUid = adminUid,
                                adminReply = adminReply.trim(),
                            )
                            actionRunning = false
                            rejectTarget = null
                            if (ok) {
                                success = "Rejected"
                                refresh()
                            } else {
                                error = "Reject failed"
                            }
                        }
                    },
                    enabled = !actionRunning,
                ) { Text("REJECT", color = colors.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { if (!actionRunning) rejectTarget = null }) {
                    Text("Cancel", color = colors.onSurfaceVariant)
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.onBackground,
            textContentColor = colors.onBackground,
            title = { Text("Reject Appeal?") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "User stays banned. Optional reason below.",
                        fontSize = 12.sp, color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = adminReply,
                        onValueChange = { adminReply = it.take(500) },
                        placeholder = { Text("e.g. Policy violation — appeal denied.") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }
}

@Composable
private fun AppealCard(
    appeal: AppealRecord,
    onResolve: () -> Unit,
    onReject: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔓", fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        appeal.userName.ifBlank { "User" },
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = colors.onBackground,
                    )
                    Text(
                        appeal.uid,
                        fontSize = 10.sp, color = colors.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                }
                Text(fmt(appeal.createdAt), fontSize = 10.sp, color = colors.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))

            Surface(shape = RoundedCornerShape(10.dp), color = colors.background, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    if (appeal.banReason.isNotBlank()) {
                        Text(
                            "Ban reason: ${appeal.banReason}",
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = colors.error,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        appeal.text,
                        fontSize = 12.sp, color = colors.onBackground,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onResolve,
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.success,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Resolve", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Button(
                    onClick = onReject,
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.error,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reject", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}