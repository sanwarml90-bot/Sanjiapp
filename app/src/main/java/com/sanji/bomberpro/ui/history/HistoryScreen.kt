package com.sanji.bomberpro.ui.history

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
import com.sanji.bomberpro.data.BlastRecord
import com.sanji.bomberpro.data.CreditTransaction
import com.sanji.bomberpro.data.OtpRecord
import com.sanji.bomberpro.services.UserDataService
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FMT_SHORT = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
private fun fmtShort(ts: Long): String =
    try { FMT_SHORT.format(Date(ts * 1000L)) } catch (_: Exception) { "—" }

@Composable
fun HistoryScreen(
    uid: String,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val userData = remember { UserDataService() }

    var currentTab by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var blasts by remember { mutableStateOf<List<BlastRecord>>(emptyList()) }
    var otps by remember { mutableStateOf<List<OtpRecord>>(emptyList()) }
    var txs by remember { mutableStateOf<List<CreditTransaction>>(emptyList()) }

    suspend fun refreshAll() {
        loading = true
        try {
            blasts = userData.getBlastRecords(uid, 50)
            otps = userData.getOtpRecords(uid, 100)
            txs = userData.getTransactionRecords(uid, 100)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refreshAll() }

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
                text = "History",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
                modifier = Modifier.weight(1f),
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
            listOf("Blasts", "OTPs", "Transactions").forEachIndexed { i, label ->
                Tab(
                    selected = currentTab == i,
                    onClick = { currentTab = i },
                    text = {
                        Text(
                            text = label,
                            fontWeight = if (currentTab == i) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
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
            0 -> BlastsTab(blasts)
            1 -> OtpsTab(otps)
            2 -> TxsTab(txs)
        }
    }
}

@Composable
private fun BlastsTab(blasts: List<BlastRecord>) {
    val colors = LocalSanjiColors.current
    if (blasts.isEmpty()) {
        EmptyView("No blasts yet")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(blasts) { b ->
            Surface(shape = RoundedCornerShape(14.dp), color = colors.surface) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = b.targetMasked.ifBlank { b.targetFull },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onBackground,
                            fontFamily = FontFamily.Monospace,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "⚡ ${b.speed}",
                            fontSize = 10.sp,
                            color = colors.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = b.message,
                        fontSize = 11.sp,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Text("✅ ${b.sent}", fontSize = 11.sp, color = colors.success, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.padding(horizontal = 6.dp))
                        Text("❌ ${b.failed}", fontSize = 11.sp, color = colors.error, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.padding(horizontal = 6.dp))
                        Text("📊 ${b.requested}", fontSize = 11.sp, color = colors.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text("⏱ ${b.durationSec}s", fontSize = 11.sp, color = colors.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("🕐 ${fmtShort(b.ts)}", fontSize = 10.sp, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun OtpsTab(otps: List<OtpRecord>) {
    val colors = LocalSanjiColors.current
    if (otps.isEmpty()) {
        EmptyView("No OTPs fetched yet")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(otps) { o ->
            Surface(shape = RoundedCornerShape(14.dp), color = colors.surface) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = o.otp,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                            fontFamily = FontFamily.Monospace,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = o.mode.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("📤 ${o.sender}", fontSize = 11.sp, color = colors.onBackground)
                    Text(
                        "📱 ${o.deviceName.ifBlank { "Device" }} · ${o.devicePhone.ifBlank { "—" }}",
                        fontSize = 10.sp,
                        color = colors.onSurfaceVariant,
                    )
                    Text("🔥 ${o.firebaseLabel.ifBlank { "Firebase" }} · 🪙 ${o.cost}", fontSize = 10.sp, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("🕐 ${fmtShort(o.ts)}", fontSize = 10.sp, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TxsTab(txs: List<CreditTransaction>) {
    val colors = LocalSanjiColors.current
    if (txs.isEmpty()) {
        EmptyView("No transactions yet")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(txs) { t ->
            val isCoin = t.type.contains("coin", ignoreCase = true)
            val isPositive = t.delta >= 0
            Surface(shape = RoundedCornerShape(14.dp), color = colors.surface) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isCoin) "🪙" else "💰",
                        fontSize = 20.sp,
                    )
                    Spacer(Modifier.padding(horizontal = 6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = humanType(t.type),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onBackground,
                        )
                        Text(
                            text = t.reason.ifBlank { "—" },
                            fontSize = 11.sp,
                            color = colors.onSurfaceVariant,
                        )
                        Text("🕐 ${fmtShort(t.ts)}", fontSize = 10.sp, color = colors.onSurfaceVariant)
                    }
                    Text(
                        text = "${if (isPositive) "+" else ""}${t.delta}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPositive) colors.success else colors.error,
                    )
                }
            }
        }
    }
}

private fun humanType(type: String): String = when (type) {
    "credit_add" -> "Credits added"
    "credit_add_manual" -> "Credits (manual)"
    "credit_deduct" -> "Credits deducted"
    "coin_add" -> "Coins added"
    "coin_add_manual" -> "Coins (manual)"
    "coin_deduct" -> "Coins deducted"
    "daily_bonus" -> "Daily bonus"
    "referral_bonus" -> "Referral bonus"
    else -> type.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
private fun EmptyView(text: String) {
    val colors = LocalSanjiColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = colors.onSurfaceVariant, fontSize = 13.sp)
    }
}