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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.data.DeletedAccount
import com.sanji.bomberpro.services.AdminService
import com.sanji.bomberpro.theme.LocalSanjiColors
import com.sanji.bomberpro.ui.components.SanjiEmoji
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FMT = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
private fun fmt(ts: Long): String =
    try { FMT.format(Date(ts * 1000L)) } catch (_: Exception) { "—" }

@Composable
fun DeletedAccountsScreen(
    adminUid: String,
    onBack: () -> Unit,
    onOpenHistory: (String) -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val service = remember { AdminService() }

    var accounts by remember { mutableStateOf<List<DeletedAccount>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<DeletedAccount?>(null) }

    suspend fun refresh() {
        loading = true
        try {
            accounts = service.listDeletedAccounts(200)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
            }
            Text(
                "Deleted Accounts (${accounts.size})",
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = colors.onBackground, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { scope.launch { refresh() } }, enabled = !loading) {
                Icon(Icons.Default.Refresh, "Refresh", tint = colors.primary)
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "Archived user snapshots. History (blasts/OTPs/transactions) preserved by UID.",
            fontSize = 11.sp, color = colors.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
            return
        }

        if (accounts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(SanjiEmoji.USER, fontSize = 42.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("No deleted accounts", fontSize = 13.sp, color = colors.onSurfaceVariant)
                }
            }
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(accounts) { a ->
                Surface(
                    onClick = { selected = a },
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                a.displayName.ifBlank { "User" },
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = colors.onBackground,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                fmt(a.deletedAt),
                                fontSize = 10.sp, color = colors.onSurfaceVariant,
                            )
                        }
                        if (a.email.isNotBlank()) {
                            Text(a.email, fontSize = 10.sp, color = colors.onSurfaceVariant, maxLines = 1)
                        }
                        Text(
                            "UID: ${a.uid}",
                            fontSize = 9.sp, color = colors.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace, maxLines = 1,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "${SanjiEmoji.CREDITS} ${a.credits}",
                                fontSize = 10.sp, color = colors.onSurfaceVariant,
                            )
                            Text(
                                "${SanjiEmoji.COINS} ${a.coins}",
                                fontSize = 10.sp, color = colors.onSurfaceVariant,
                            )
                            Text(
                                "${SanjiEmoji.SEND} ${a.totalSms}",
                                fontSize = 10.sp, color = colors.onSurfaceVariant,
                            )
                        }
                        if (a.reason.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Reason: ${a.reason}",
                                fontSize = 10.sp, color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    val sel = selected
    if (sel != null) {
        AlertDialog(
            onDismissRequest = { selected = null },
            confirmButton = {
                TextButton(onClick = {
                    onOpenHistory(sel.uid)
                    selected = null
                }) {
                    Text("View History", color = colors.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selected = null }) {
                    Text("Close", color = colors.onSurfaceVariant)
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.onBackground,
            textContentColor = colors.onBackground,
            title = { Text("${SanjiEmoji.USER} Deleted Account") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    InfoRow("Name", sel.displayName.ifBlank { "—" })
                    InfoRow("Email", sel.email.ifBlank { "—" })
                    InfoRow("Username", "@${sel.username.ifBlank { "—" }}")
                    InfoRow("Role", sel.role.uppercase())
                    InfoRow("UID", sel.uid)
                    Spacer(Modifier.height(8.dp))
                    InfoRow("Credits", "${sel.credits}")
                    InfoRow("Coins", "${sel.coins}")
                    InfoRow("Total Blasts", "${sel.totalBlasts}")
                    InfoRow("Total SMS", "${sel.totalSms}")
                    InfoRow("Total OTPs", "${sel.totalOtps}")
                    Spacer(Modifier.height(8.dp))
                    InfoRow("Created", fmt(sel.createdAt))
                    InfoRow("Last Active", fmt(sel.lastActive))
                    InfoRow("Deleted", fmt(sel.deletedAt))
                    InfoRow("Deleted By", sel.deletedBy.ifBlank { "—" })
                    if (sel.reason.isNotBlank()) InfoRow("Reason", sel.reason)
                    if (sel.deviceFingerprint.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Device fingerprint:",
                            fontSize = 10.sp, color = colors.onSurfaceVariant,
                        )
                        Text(
                            sel.deviceFingerprint.take(32),
                            fontSize = 9.sp, color = colors.onBackground,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = LocalSanjiColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 11.sp, color = colors.onSurfaceVariant)
        Text(
            value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            color = colors.onBackground, maxLines = 2,
        )
    }
}