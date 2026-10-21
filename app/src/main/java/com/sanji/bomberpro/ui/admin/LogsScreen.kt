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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import com.sanji.bomberpro.services.ActivityLogEntry
import com.sanji.bomberpro.services.AuditLogEntry
import com.sanji.bomberpro.services.LogsService
import com.sanji.bomberpro.services.PaymentLogEntry
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FMT_SHORT = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
private fun fmtShort(ts: Long): String =
    try { FMT_SHORT.format(Date(ts * 1000L)) } catch (_: Exception) { "—" }

@Composable
fun LogsScreen(
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val service = remember { LogsService() }

    var currentTab by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var activityLogs by remember { mutableStateOf<List<ActivityLogEntry>>(emptyList()) }
    var auditLogs by remember { mutableStateOf<List<AuditLogEntry>>(emptyList()) }
    var paymentLogs by remember { mutableStateOf<List<PaymentLogEntry>>(emptyList()) }

    suspend fun refreshAll() {
        loading = true
        activityLogs = service.listActivityLogs(200)
        auditLogs = service.listAuditLogs(200)
        paymentLogs = service.listPaymentLogs(200)
        loading = false
    }

    LaunchedEffect(Unit) { refreshAll() }

    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
            }
            Text(
                "Logs",
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = colors.onBackground, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { scope.launch { refreshAll() } }) {
                Icon(Icons.Default.Refresh, "Refresh", tint = colors.primary)
            }
        }

        Spacer(Modifier.height(8.dp))

        ScrollableTabRow(
            selectedTabIndex = currentTab,
            containerColor = Color.Transparent,
            contentColor = colors.primary,
            edgePadding = 0.dp,
        ) {
            listOf(
                "Activity (${activityLogs.size})",
                "Audit (${auditLogs.size})",
                "Payments (${paymentLogs.size})",
            ).forEachIndexed { i, label ->
                Tab(
                    selected = currentTab == i,
                    onClick = { currentTab = i },
                    text = {
                        Text(
                            label,
                            fontWeight = if (currentTab == i) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp,
                        )
                    },
                    selectedContentColor = colors.primary,
                    unselectedContentColor = colors.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
            return
        }

        when (currentTab) {
            0 -> ActivityTab(activityLogs)
            1 -> AuditTab(auditLogs)
            2 -> PaymentsTab(paymentLogs)
        }
    }
}

@Composable
private fun ActivityTab(logs: List<ActivityLogEntry>) {
    val colors = LocalSanjiColors.current
    if (logs.isEmpty()) {
        EmptyView("No activity logs")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(logs) { log ->
            Surface(shape = RoundedCornerShape(12.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(emojiForAction(log.action), fontSize = 16.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            humanAction(log.action),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        Text(fmtShort(log.ts), fontSize = 10.sp, color = colors.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${log.userName.ifBlank { "?" }} · ${log.userEmail.ifBlank { "—" }}",
                        fontSize = 10.sp, color = colors.onSurfaceVariant, maxLines = 1,
                    )
                    Text(
                        "UID: ${log.uid}",
                        fontSize = 9.sp, color = colors.onSurfaceVariant, fontFamily = FontFamily.Monospace,
                    )
                    if (log.targetFull.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "📞 ${log.targetFull}",
                            fontSize = 11.sp, color = colors.primary, fontFamily = FontFamily.Monospace,
                        )
                    }
                    if (log.details.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        log.details.entries.take(5).forEach { (k, v) ->
                            Text(
                                "$k: ${v.toString().take(60)}",
                                fontSize = 10.sp, color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditTab(logs: List<AuditLogEntry>) {
    val colors = LocalSanjiColors.current
    if (logs.isEmpty()) {
        EmptyView("No audit logs")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(logs) { log ->
            Surface(shape = RoundedCornerShape(12.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(emojiForAudit(log.action), fontSize = 16.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            humanAudit(log.action),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        Text(fmtShort(log.ts), fontSize = 10.sp, color = colors.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "By: ${log.adminUid}",
                        fontSize = 10.sp, color = colors.onSurfaceVariant, fontFamily = FontFamily.Monospace,
                    )
                    if (log.targetUid.isNotBlank() && log.targetUid != "0") {
                        Text(
                            "Target: ${log.targetUid}",
                            fontSize = 10.sp, color = colors.onSurfaceVariant, fontFamily = FontFamily.Monospace,
                        )
                    }
                    if (log.amount != 0) {
                        Text(
                            "Amount: ${log.amount}",
                            fontSize = 11.sp, color = colors.primary, fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (log.reason.isNotBlank()) {
                        Text(
                            "Note: ${log.reason.take(80)}",
                            fontSize = 10.sp, color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentsTab(logs: List<PaymentLogEntry>) {
    val colors = LocalSanjiColors.current
    if (logs.isEmpty()) {
        EmptyView("No payment logs")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(logs) { log ->
            val statusEmoji = when (log.status) {
                "approved" -> "✅"
                "rejected" -> "❌"
                else -> "⏳"
            }
            Surface(shape = RoundedCornerShape(12.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(statusEmoji, fontSize = 16.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "₹${log.amount} · ${log.status.uppercase()}",
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        Text(fmtShort(log.createdAt), fontSize = 10.sp, color = colors.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${log.userName.ifBlank { "?" }} · ${log.userEmail.ifBlank { "—" }}",
                        fontSize = 10.sp, color = colors.onSurfaceVariant,
                    )
                    Text(
                        if (log.type == "subscription") "📦 ${log.plan.uppercase()}"
                        else "📦 ${log.packName} (${log.creditsAmount} credits)",
                        fontSize = 11.sp, color = colors.onBackground,
                    )
                    Text(
                        "UTR: ${log.utr}",
                        fontSize = 10.sp, color = colors.primary, fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        "UID: ${log.uid}",
                        fontSize = 9.sp, color = colors.onSurfaceVariant, fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyView(text: String) {
    val colors = LocalSanjiColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = colors.onSurfaceVariant, fontSize = 13.sp)
    }
}

private fun emojiForAction(a: String): String = when {
    a.contains("blast") -> "🚀"
    a.contains("otp") -> "🔐"
    a.contains("redeem") -> "🎁"
    a.contains("referral") -> "👥"
    a.contains("payment") -> "💳"
    a.contains("ticket") -> "📩"
    a.contains("login") -> "🔑"
    a.contains("terms") -> "📜"
    else -> "📝"
}

private fun humanAction(a: String): String = a.replace('_', ' ').split(" ")
    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

private fun emojiForAudit(a: String): String = when {
    a.contains("credit") -> "💰"
    a.contains("coin") -> "🪙"
    a.contains("ban") -> "🚫"
    a.contains("unban") -> "✅"
    a.contains("role") -> "🔱"
    a.contains("sub") -> "🏆"
    a.contains("premium") -> "💎"
    a.contains("transfer_owner") -> "👑"
    a.contains("set_limit") -> "🔒"
    a.contains("notes") -> "📝"
    a.contains("tags") -> "🏷"
    else -> "📋"
}

private fun humanAudit(a: String): String {
    val cleaned = a.removePrefix("admin_")
    return cleaned.replace('_', ' ').split(" ")
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}