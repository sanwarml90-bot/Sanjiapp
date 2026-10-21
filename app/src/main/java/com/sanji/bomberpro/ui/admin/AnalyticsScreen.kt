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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sanji.bomberpro.FirebaseConfig
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

private data class AnalyticsData(
    val totalUsers: Int = 0,
    val activeSubs: Int = 0,
    val totalCredits: Long = 0L,
    val totalCoins: Long = 0L,
    val revenueTotal: Long = 0L,
    val revenueToday: Long = 0L,
    val usersToday: Int = 0,
    val blastsToday: Int = 0,
    val smsToday: Long = 0L,
    val otpsToday: Int = 0,
    val pendingPayments: Int = 0,
)

private fun istMidnightTs(): Long {
    val tz = java.util.TimeZone.getTimeZone("Asia/Kolkata")
    val cal = java.util.Calendar.getInstance(tz)
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis / 1000L
}

private suspend fun loadAnalytics(): AnalyticsData = withContext(Dispatchers.IO) {
    val db = FirebaseFirestore.getInstance()
    val now = System.currentTimeMillis() / 1000L
    val midnight = istMidnightTs()

    var totalUsers = 0
    var activeSubs = 0
    var totalCredits = 0L
    var totalCoins = 0L
    var usersToday = 0

    try {
        val users = db.collection(FirebaseConfig.COL_USERS).get().await().documents
        totalUsers = users.size
        for (u in users) {
            val cred = u.getLong("credits") ?: 0L
            val coin = u.getLong("coins") ?: 0L
            totalCredits += cred
            totalCoins += coin
            val sub = u.get("subscription") as? Map<*, *>
            val plan = sub?.get("plan") as? String ?: "free"
            val expiry = (sub?.get("expiresAt") as? Number)?.toLong() ?: 0L
            if (plan != "free" && expiry > now) activeSubs++
            val created = u.getLong("createdAt") ?: 0L
            if (created >= midnight) usersToday++
        }
    } catch (_: Exception) {}

    var blastsToday = 0
    var smsToday = 0L
    try {
        val blasts = db.collection(FirebaseConfig.COL_BLASTS)
            .whereGreaterThanOrEqualTo("ts", midnight)
            .get().await().documents
        blastsToday = blasts.size
        for (b in blasts) {
            smsToday += b.getLong("sent") ?: 0L
        }
    } catch (_: Exception) {}

    var otpsToday = 0
    try {
        otpsToday = db.collection(FirebaseConfig.COL_OTP_FETCHES)
            .whereGreaterThanOrEqualTo("ts", midnight)
            .get().await().size()
    } catch (_: Exception) {}

    var revenueTotal = 0L
    var revenueToday = 0L
    var pendingPayments = 0
    try {
        val payments = db.collection(FirebaseConfig.COL_PAYMENT_REQUESTS).get().await().documents
        for (p in payments) {
            val status = p.getString("status") ?: "pending"
            val amt = p.getLong("amount") ?: 0L
            if (status == "approved") {
                revenueTotal += amt
                val reviewed = p.getLong("reviewedAt") ?: 0L
                if (reviewed >= midnight) revenueToday += amt
            } else if (status == "pending") {
                pendingPayments++
            }
        }
    } catch (_: Exception) {}

    AnalyticsData(
        totalUsers = totalUsers,
        activeSubs = activeSubs,
        totalCredits = totalCredits,
        totalCoins = totalCoins,
        revenueTotal = revenueTotal,
        revenueToday = revenueToday,
        usersToday = usersToday,
        blastsToday = blastsToday,
        smsToday = smsToday,
        otpsToday = otpsToday,
        pendingPayments = pendingPayments,
    )
}

@Composable
fun AnalyticsScreen(onBack: () -> Unit) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()

    var data by remember { mutableStateOf<AnalyticsData?>(null) }
    var loading by remember { mutableStateOf(true) }

    suspend fun refresh() {
        loading = true
        data = loadAnalytics()
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

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
                "Analytics",
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = colors.onBackground, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { scope.launch { refresh() } }) {
                Icon(Icons.Default.Refresh, "Refresh", tint = colors.primary)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (loading || data == null) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
            return
        }

        val d = data!!

        // Overall
        Text("📊 Overall", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("👥", "Users", "${d.totalUsers}", colors.primary, Modifier.weight(1f))
            MetricCard("🏆", "Active Subs", "${d.activeSubs}", colors.success, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("💰", "Total Credits", "${d.totalCredits}", colors.primary, Modifier.weight(1f))
            MetricCard("🪙", "Total Coins", "${d.totalCoins}", colors.primary, Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))
        Text("💵 Revenue", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
        Spacer(Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(colors = listOf(colors.primary, colors.secondary)))
                    .padding(20.dp),
            ) {
                Column {
                    Text("TOTAL REVENUE", fontSize = 10.sp, color = Color.Black.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("₹${d.revenueTotal}", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    Spacer(Modifier.height(6.dp))
                    Text("Today: ₹${d.revenueToday}", fontSize = 12.sp, color = Color.Black.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("📅 Today", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("🆕", "New Users", "${d.usersToday}", colors.primary, Modifier.weight(1f))
            MetricCard("🚀", "Blasts", "${d.blastsToday}", colors.primary, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("📤", "SMS", "${d.smsToday}", colors.primary, Modifier.weight(1f))
            MetricCard("🔐", "OTPs", "${d.otpsToday}", colors.primary, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("⏳", "Pending Pay", "${d.pendingPayments}", colors.warning, Modifier.weight(1f))
            MetricCard("💵", "Rev Today", "₹${d.revenueToday}", colors.success, Modifier.weight(1f))
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MetricCard(
    emoji: String,
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSanjiColors.current
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = colors.surface) {
        Column(Modifier.padding(12.dp)) {
            Text(emoji, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = valueColor)
            Text(label, fontSize = 10.sp, color = colors.onSurfaceVariant)
        }
    }
}