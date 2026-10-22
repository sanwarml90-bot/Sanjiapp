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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.services.AdminService
import com.sanji.bomberpro.services.AdminUser
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FMT_SHORT = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
private fun fmtShort(ts: Long): String =
    try { FMT_SHORT.format(Date(ts * 1000L)) } catch (_: Exception) { "—" }

@Composable
fun UserDetailSection(
    user: AdminUser,
    adminUid: String,
    isOwner: Boolean,
    service: AdminService,
    onBack: () -> Unit,
    onOpenSmsHistory: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()

    var showCreditsAdd by remember { mutableStateOf(false) }
    var showCreditsDeduct by remember { mutableStateOf(false) }
    var showCoinsAdd by remember { mutableStateOf(false) }
    var showCoinsDeduct by remember { mutableStateOf(false) }
    var showBan by remember { mutableStateOf(false) }
    var showSendLimit by remember { mutableStateOf(false) }
    var showOtpLimit by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    var showSub by remember { mutableStateOf(false) }

    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
            }
            Text(
                "User Detail",
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = colors.onBackground, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, "Refresh", tint = colors.primary)
            }
        }

        Spacer(Modifier.height(12.dp))

        // User info card
        Surface(shape = RoundedCornerShape(14.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                DetailRow("Name", user.displayName)
                if (user.username.isNotBlank()) DetailRow("Username", "@${user.username}")
                DetailRow("Email", user.email)
                DetailRow("UID", user.uid)
                DetailRow("Role", user.role.replace('_', ' ').uppercase())
                DetailRow("Terms", if (user.termsAccepted) "✅ Accepted" else "❌ Pending")
                DetailRow("Banned", if (user.banned) "🚫 ${user.banReason.ifBlank { "yes" }}" else "No")
                DetailRow("Premium", if (user.premium) "💎 Yes" else "No")
                DetailRow(
                    "Plan",
                    if (user.subscriptionPlan != "free" && user.subscriptionExpiresAt > 0)
                        "${user.subscriptionPlan.uppercase()} · till ${fmtShort(user.subscriptionExpiresAt)}"
                    else "Free"
                )
                DetailRow("Refer Code", user.referCode.ifBlank { "—" })
                DetailRow("Referred By", user.referredBy ?: "—")
                DetailRow("Joined", fmtShort(user.createdAt))
                DetailRow("Last Active", fmtShort(user.lastActive))
                DetailRow("Total SMS", "${user.totalSms}")
                DetailRow("Total OTPs", "${user.totalOtps}")
                DetailRow("Total Blasts", "${user.totalBlasts}")
            }
        }

        Spacer(Modifier.height(16.dp))

        DeviceInfoCard(user = user)

        Spacer(Modifier.height(16.dp))

        // Balances
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = colors.surface, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(14.dp)) {
                    Text("💰 Credits", fontSize = 11.sp, color = colors.onSurfaceVariant)
                    Text("${user.credits}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmallBtn("+", colors.success) { showCreditsAdd = true }
                        SmallBtn("−", colors.error) { showCreditsDeduct = true }
                    }
                }
            }
            Surface(shape = RoundedCornerShape(14.dp), color = colors.surface, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(14.dp)) {
                    Text("🪙 Coins", fontSize = 11.sp, color = colors.onSurfaceVariant)
                    Text("${user.coins}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmallBtn("+", colors.success) { showCoinsAdd = true }
                        SmallBtn("−", colors.error) { showCoinsDeduct = true }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text("Actions", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        Spacer(Modifier.height(8.dp))

        // View SMS history — owner only
        ActionCard(
            emoji = "📜",
            title = "View SMS History",
            subtitle = "All blasts sent by this user · target + message",
            onClick = onOpenSmsHistory,
        )

        ActionCard(
            emoji = if (user.banned) "✅" else "🚫",
            title = if (user.banned) "Unban user" else "Ban user",
            subtitle = if (user.banned) "Reason: ${user.banReason.ifBlank { "—" }}" else "Block user from using the app",
            onClick = {
                if (user.banned) {
                    scope.launch {
                        val ok = service.unbanUser(user.uid, adminUid)
                        if (ok) { success = "Unbanned"; onRefresh() } else error = "Failed"
                    }
                } else {
                    showBan = true
                }
            },
        )

        val sendLimitStr = user.maxSendLimit?.let { if (it <= 0) "Blocked" else "$it" } ?: "Unlimited"
        val otpLimitStr = user.maxOtpLimit?.let { if (it <= 0) "Blocked" else "$it" } ?: "Unlimited"
        ActionCard(
            emoji = "🔒",
            title = "SMS Limit",
            subtitle = "Current: $sendLimitStr",
            onClick = { showSendLimit = true },
        )
        ActionCard(
            emoji = "🔐",
            title = "OTP Limit",
            subtitle = "Current: $otpLimitStr",
            onClick = { showOtpLimit = true },
        )

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("💎", fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Premium", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                    Text("Free unlimited SMS/OTP", fontSize = 11.sp, color = colors.onSurfaceVariant)
                }
                Switch(
                    checked = user.premium,
                    onCheckedChange = { checked ->
                        scope.launch {
                            val ok = service.setPremium(user.uid, checked, adminUid)
                            if (ok) { success = "Premium ${if (checked) "ON" else "OFF"}"; onRefresh() } else error = "Failed"
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.primary,
                        checkedTrackColor = colors.primary.copy(alpha = 0.4f),
                    ),
                )
            }
        }

        ActionCard(
            emoji = "🏆",
            title = "Subscription",
            subtitle = if (user.subscriptionPlan != "free" && user.subscriptionExpiresAt > 0)
                "${user.subscriptionPlan.uppercase()} · till ${fmtShort(user.subscriptionExpiresAt)}"
            else "Free · tap to grant",
            onClick = { showSub = true },
        )

        ActionCard(
            emoji = "📝",
            title = "Notes",
            subtitle = user.notes.ifBlank { "No notes" }.take(60),
            onClick = { showNotes = true },
        )

        ActionCard(
            emoji = "🏷",
            title = "Tags",
            subtitle = if (user.tags.isEmpty()) "No tags" else user.tags.joinToString(" ") { "#$it" },
            onClick = { showTags = true },
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = colors.error, fontSize = 12.sp)
        }
        if (success != null) {
            Spacer(Modifier.height(12.dp))
            Text(success!!, color = colors.success, fontSize = 12.sp)
        }

        Spacer(Modifier.height(24.dp))

        if (showCreditsAdd) AmountDialog("Add Credits", onDismiss = { showCreditsAdd = false }) { amt ->
            scope.launch {
                val ok = service.addCreditsToUser(user.uid, amt, adminUid, "admin-add")
                showCreditsAdd = false
                if (ok) { success = "+$amt credits"; onRefresh() } else error = "Failed"
            }
        }
        if (showCreditsDeduct) AmountDialog("Deduct Credits", onDismiss = { showCreditsDeduct = false }) { amt ->
            scope.launch {
                val ok = service.deductCreditsFromUser(user.uid, amt, adminUid)
                showCreditsDeduct = false
                if (ok) { success = "-$amt credits"; onRefresh() } else error = "Failed — insufficient"
            }
        }
        if (showCoinsAdd) AmountDialog("Add Coins", onDismiss = { showCoinsAdd = false }) { amt ->
            scope.launch {
                val ok = service.addCoinsToUser(user.uid, amt, adminUid)
                showCoinsAdd = false
                if (ok) { success = "+$amt coins"; onRefresh() } else error = "Failed"
            }
        }
        if (showCoinsDeduct) AmountDialog("Deduct Coins", onDismiss = { showCoinsDeduct = false }) { amt ->
            scope.launch {
                val ok = service.deductCoinsFromUser(user.uid, amt, adminUid)
                showCoinsDeduct = false
                if (ok) { success = "-$amt coins"; onRefresh() } else error = "Failed — insufficient"
            }
        }

        if (showBan) BanDialog(userName = user.displayName, onDismiss = { showBan = false }) { reason ->
            scope.launch {
                val ok = service.banUser(user.uid, reason, adminUid)
                showBan = false
                if (ok) { success = "Banned"; onRefresh() } else error = "Failed"
            }
        }

        if (showSendLimit) LimitDialog(
            title = "SMS Limit",
            current = user.maxSendLimit,
            onDismiss = { showSendLimit = false },
        ) { limit ->
            scope.launch {
                val ok = service.setSendLimit(user.uid, limit, adminUid)
                showSendLimit = false
                if (ok) { success = "SMS limit updated"; onRefresh() } else error = "Failed"
            }
        }

        if (showOtpLimit) LimitDialog(
            title = "OTP Limit",
            current = user.maxOtpLimit,
            onDismiss = { showOtpLimit = false },
        ) { limit ->
            scope.launch {
                val ok = service.setOtpLimit(user.uid, limit, adminUid)
                showOtpLimit = false
                if (ok) { success = "OTP limit updated"; onRefresh() } else error = "Failed"
            }
        }

        if (showNotes) NotesDialog(
            current = user.notes,
            onDismiss = { showNotes = false },
        ) { notes ->
            scope.launch {
                val ok = service.setNotes(user.uid, notes, adminUid)
                showNotes = false
                if (ok) { success = "Notes saved"; onRefresh() } else error = "Failed"
            }
        }

        if (showTags) TagsDialog(
            current = user.tags,
            onDismiss = { showTags = false },
        ) { tags ->
            scope.launch {
                val ok = service.setTags(user.uid, tags, adminUid)
                showTags = false
                if (ok) { success = "Tags saved"; onRefresh() } else error = "Failed"
            }
        }

        if (showSub) SubscriptionDialog(
            currentPlan = user.subscriptionPlan,
            currentExpiry = user.subscriptionExpiresAt,
            onDismiss = { showSub = false },
            onGrant = { plan, days ->
                scope.launch {
                    val ok = service.grantSubscription(user.uid, plan, days, adminUid)
                    showSub = false
                    if (ok) { success = "$plan granted ${days}d"; onRefresh() } else error = "Failed"
                }
            },
            onRevoke = {
                scope.launch {
                    val ok = service.revokeSubscription(user.uid, adminUid)
                    showSub = false
                    if (ok) { success = "Subscription revoked"; onRefresh() } else error = "Failed"
                }
            },
        )
    }
}

@Composable
private fun DeviceInfoCard(user: AdminUser) {
    val colors = LocalSanjiColors.current
    val hasAnySim = user.sim1Number.isNotBlank() || user.sim2Number.isNotBlank()
    val hasDevice = user.deviceModel.isNotBlank() || user.deviceBrand.isNotBlank()

    Surface(shape = RoundedCornerShape(14.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📱", fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Device Info",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = colors.onBackground, modifier = Modifier.weight(1f),
                )
                if (user.deviceReportedAt > 0) {
                    Text(fmtShort(user.deviceReportedAt), fontSize = 10.sp, color = colors.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (!hasAnySim && !hasDevice) {
                Text("No device info reported yet", fontSize = 11.sp, color = colors.onSurfaceVariant)
                return@Column
            }
            if (hasDevice) {
                DeviceRow("Device", user.deviceModel.ifBlank { "—" })
                DeviceRow("Brand", user.deviceBrand.ifBlank { "—" })
                if (user.androidVersion.isNotBlank()) {
                    DeviceRow("Android", "${user.androidVersion}${if (user.sdkInt > 0) " (SDK ${user.sdkInt})" else ""}")
                }
                Spacer(Modifier.height(8.dp))
            }
            if (user.sim1Number.isNotBlank() || user.sim1Carrier.isNotBlank()) {
                SimBlock("SIM 1", user.sim1Number, user.sim1Carrier, user.sim1Source)
            } else {
                SimBlock("SIM 1", "", "", "not detected")
            }
            if (user.sim2Number.isNotBlank() || user.sim2Carrier.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                SimBlock("SIM 2", user.sim2Number, user.sim2Carrier, user.sim2Source)
            }
        }
    }
}

@Composable
private fun SimBlock(label: String, number: String, carrier: String, source: String) {
    val colors = LocalSanjiColors.current
    Surface(shape = RoundedCornerShape(10.dp), color = colors.background, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                if (source.isNotBlank() && source != "none") {
                    Spacer(Modifier.width(6.dp))
                    Text("via $source", fontSize = 9.sp, color = colors.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(2.dp))
            if (number.isNotBlank()) {
                Text(number, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, fontFamily = FontFamily.Monospace)
            } else {
                Text("number not detected", fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
            if (carrier.isNotBlank()) {
                Text(carrier, fontSize = 11.sp, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DeviceRow(label: String, value: String) {
    val colors = LocalSanjiColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 11.sp, color = colors.onSurfaceVariant)
        Text(
            value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = colors.onBackground, maxLines = 1,
            textAlign = TextAlign.End, modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun ActionCard(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    val colors = LocalSanjiColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                Text(subtitle, fontSize = 11.sp, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SmallBtn(label: String, color: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.15f)) {
        Text(
            label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val colors = LocalSanjiColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 11.sp, color = colors.onSurfaceVariant)
        Text(
            value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = colors.onBackground, maxLines = 2, textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun AmountDialog(title: String, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val colors = LocalSanjiColors.current
    var text by remember { mutableStateOf("") }
    val amt = text.toIntOrNull() ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { if (amt > 0) onConfirm(amt) }, enabled = amt > 0) {
                Text("Confirm", color = colors.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.onSurfaceVariant) } },
        containerColor = colors.surface,
        titleContentColor = colors.onBackground,
        textContentColor = colors.onBackground,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() } },
                label = { Text("Amount") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun BanDialog(userName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val colors = LocalSanjiColors.current
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(reason.trim()) }) {
                Text("Ban $userName", color = colors.error, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.onSurfaceVariant) } },
        containerColor = colors.surface,
        titleContentColor = colors.onBackground,
        textContentColor = colors.onBackground,
        title = { Text("Ban User") },
        text = {
            Column {
                Text("Reason for banning:", fontSize = 12.sp, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(200) },
                    placeholder = { Text("e.g. Spam abuse") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        },
    )
}

@Composable
private fun LimitDialog(title: String, current: Int?, onDismiss: () -> Unit, onConfirm: (Int?) -> Unit) {
    val colors = LocalSanjiColors.current
    var mode by remember { mutableStateOf(if (current == null) 0 else if (current <= 0) 1 else 2) }
    var custom by remember { mutableStateOf(if (current != null && current > 0) "$current" else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val value: Int? = when (mode) {
                    0 -> null
                    1 -> 0
                    else -> custom.toIntOrNull()
                }
                if (mode == 2 && (value == null || value <= 0)) return@TextButton
                onConfirm(value)
            }) { Text("Save", color = colors.primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.onSurfaceVariant) } },
        containerColor = colors.surface,
        titleContentColor = colors.onBackground,
        textContentColor = colors.onBackground,
        title = { Text(title) },
        text = {
            Column {
                RadioRow("Unlimited", mode == 0) { mode = 0 }
                RadioRow("Block (0)", mode == 1) { mode = 1 }
                RadioRow("Custom limit", mode == 2) { mode = 2 }
                if (mode == 2) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it.filter { c -> c.isDigit() } },
                        label = { Text("Amount") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalSanjiColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 13.sp, color = colors.onBackground)
    }
}

@Composable
private fun NotesDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val colors = LocalSanjiColors.current
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }) {
                Text("Save", color = colors.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.onSurfaceVariant) } },
        containerColor = colors.surface,
        titleContentColor = colors.onBackground,
        textContentColor = colors.onBackground,
        title = { Text("Notes") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(500) },
                placeholder = { Text("Admin notes about this user…") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 8,
            )
        },
    )
}

@Composable
private fun TagsDialog(current: List<String>, onDismiss: () -> Unit, onConfirm: (List<String>) -> Unit) {
    val colors = LocalSanjiColors.current
    var text by remember { mutableStateOf(current.joinToString(", ")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val tags = text.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(10)
                onConfirm(tags)
            }) { Text("Save", color = colors.primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.onSurfaceVariant) } },
        containerColor = colors.surface,
        titleContentColor = colors.onBackground,
        textContentColor = colors.onBackground,
        title = { Text("Tags") },
        text = {
            Column {
                Text("Comma-separated. Max 10.", fontSize = 11.sp, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(300) },
                    placeholder = { Text("vip, tester, fraud-suspect") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun SubscriptionDialog(
    currentPlan: String,
    currentExpiry: Long,
    onDismiss: () -> Unit,
    onGrant: (String, Int) -> Unit,
    onRevoke: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val hasActive = currentPlan != "free" && currentExpiry > System.currentTimeMillis() / 1000L
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = colors.onSurfaceVariant) } },
        containerColor = colors.surface,
        titleContentColor = colors.onBackground,
        textContentColor = colors.onBackground,
        title = { Text("Subscription") },
        text = {
            Column {
                Text(
                    if (hasActive) "Current: ${currentPlan.uppercase()} (till ${fmtShort(currentExpiry)})"
                    else "Currently on Free plan",
                    fontSize = 12.sp, color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                PlanButton("Pro · 30 days", colors.primary) { onGrant("pro", 30) }
                PlanButton("Pro · 7 days", colors.primary) { onGrant("pro", 7) }
                PlanButton("Elite · 30 days", colors.secondary) { onGrant("elite", 30) }
                PlanButton("Elite · 7 days", colors.secondary) { onGrant("elite", 7) }
                if (hasActive) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onRevoke,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.error.copy(alpha = 0.15f),
                            contentColor = colors.error,
                        ),
                    ) { Text("Revoke Subscription", fontWeight = FontWeight.Bold) }
                }
            }
        },
    )
}

@Composable
private fun PlanButton(label: String, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.15f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Text(
            label,
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}