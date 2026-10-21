package com.sanji.bomberpro.ui.wallet

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material.icons.filled.WorkspacePremium
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.data.CreditTransaction
import com.sanji.bomberpro.data.GlobalSettings
import com.sanji.bomberpro.services.CreditPack
import com.sanji.bomberpro.services.PricingService
import com.sanji.bomberpro.services.SubscriptionPlan
import com.sanji.bomberpro.services.UserDataService
import com.sanji.bomberpro.services.UserDocObserver
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FMT_SHORT = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
private fun fmtShort(ts: Long): String =
    try { FMT_SHORT.format(Date(ts * 1000L)) } catch (_: Exception) { "—" }

@Composable
fun WalletScreen(
    uid: String,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val userData = remember { UserDataService() }
    val pricing = remember { PricingService() }

    var credits by remember { mutableStateOf(0) }
    var coins by remember { mutableStateOf(0) }
    var subPlan by remember { mutableStateOf("free") }
    var subExpiresAt by remember { mutableStateOf(0L) }
    var packs by remember { mutableStateOf<List<CreditPack>>(emptyList()) }
    var plans by remember { mutableStateOf<List<SubscriptionPlan>>(emptyList()) }
    var settings by remember { mutableStateOf(GlobalSettings()) }
    var txs by remember { mutableStateOf<List<CreditTransaction>>(emptyList()) }
    var dailyBonusRemaining by remember { mutableStateOf(0L) }
    var loading by remember { mutableStateOf(true) }
    var showRedeem by remember { mutableStateOf(false) }
    var buyTarget by remember { mutableStateOf<BuyTarget?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    // Load initial
    LaunchedEffect(Unit) {
        settings = userData.getGlobalSettings()
        packs = pricing.loadCreditPacks()
        plans = pricing.loadSubscriptionPlans()
        txs = userData.getTransactionRecords(uid, 5)
        dailyBonusRemaining = userData.getDailyBonusRemaining(uid)
        loading = false
    }

    // Live doc
    LaunchedEffect(uid) {
        UserDocObserver.observeUserDoc(uid).collect { map ->
            credits = (map["credits"] as? Number)?.toInt() ?: 0
            coins = (map["coins"] as? Number)?.toInt() ?: 0
            val sub = map["subscription"] as? Map<*, *>
            subPlan = (sub?.get("plan") as? String) ?: "free"
            subExpiresAt = (sub?.get("expiresAt") as? Number)?.toLong() ?: 0L
        }
    }

    // Buy dialog screen
    val activeBuy = buyTarget
    if (activeBuy != null) {
        BuyInstructionsModal(
            target = activeBuy,
            upiId = settings.upiId,
            upiName = settings.upiName,
            onClose = { buyTarget = null },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Top bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
            }
            Text(
                "Wallet",
                fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = colors.onBackground, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                scope.launch {
                    txs = userData.getTransactionRecords(uid, 5)
                    dailyBonusRemaining = userData.getDailyBonusRemaining(uid)
                }
            }) {
                Icon(Icons.Default.Refresh, "Refresh", tint = colors.primary)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Hero balance card
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(colors.primary, colors.secondary)
                        )
                    )
                    .padding(20.dp),
            ) {
                Column {
                    Text(
                        "Your balance",
                        fontSize = 11.sp, color = Color.Black.copy(alpha = 0.7f),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$credits",
                            fontSize = 38.sp, fontWeight = FontWeight.Black,
                            color = Color.Black, fontFamily = FontFamily.Monospace,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "credits",
                                fontSize = 12.sp, color = Color.Black.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🪙", fontSize = 12.sp)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "$coins coins",
                                    fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Action row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(
                icon = Icons.Default.ShoppingCart,
                label = "Buy",
                onClick = {
                    if (packs.isNotEmpty()) {
                        buyTarget = BuyTarget.Credits(packs.first())
                    } else {
                        error = "Packs not loaded"
                    }
                },
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                icon = Icons.Default.CheckCircle,
                label = "Redeem",
                onClick = { showRedeem = true },
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                icon = Icons.Default.MonetizationOn,
                label = if (dailyBonusRemaining > 0) "Bonus\n${fmtDur(dailyBonusRemaining)}" else "Bonus",
                onClick = {
                    scope.launch {
                        val amount = userData.tryClaimDailyBonus(uid)
                        if (amount != null && amount > 0) {
                            success = "+$amount credits claimed!"
                            dailyBonusRemaining = userData.getDailyBonusRemaining(uid)
                        } else {
                            error = "Not ready — come back later"
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }

        // Subscription status
        if (subPlan != "free" && subExpiresAt > System.currentTimeMillis() / 1000L) {
            Spacer(Modifier.height(16.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WorkspacePremium, null, tint = colors.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${subPlan.uppercase()} active",
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onBackground,
                        )
                        Text(
                            "Expires: ${fmtShort(subExpiresAt)}",
                            fontSize = 11.sp, color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
            return
        }

        // Credit packs
        if (packs.isNotEmpty()) {
            Text(
                "Buy Credits",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            packs.forEach { pack ->
                Surface(
                    onClick = { buyTarget = BuyTarget.Credits(pack) },
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surface,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("💰", fontSize = 22.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pack.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                            Text("${pack.credits} credits", fontSize = 11.sp, color = colors.onSurfaceVariant)
                        }
                        Text(
                            "₹${pack.priceInr}",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // Subscription plans
        if (plans.isNotEmpty()) {
            Text(
                "Upgrade Subscription",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            plans.forEach { plan ->
                Surface(
                    onClick = { buyTarget = BuyTarget.Subscription(plan) },
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surface,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WorkspacePremium, null, tint = colors.primary, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(plan.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                            Text(
                                "${plan.durationDays} days · ${plan.dailyLimit} SMS/day",
                                fontSize = 11.sp, color = colors.onSurfaceVariant,
                            )
                        }
                        Text(
                            "₹${plan.priceInr}",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // Recent tx
        if (txs.isNotEmpty()) {
            Text(
                "Recent Transactions",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            txs.forEach { t ->
                val isCoin = t.type.contains("coin", true)
                val positive = t.delta >= 0
                Surface(shape = RoundedCornerShape(12.dp), color = colors.surface, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isCoin) "🪙" else "💰", fontSize = 16.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(humanType(t.type), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                            Text(t.reason.ifBlank { "—" }, fontSize = 10.sp, color = colors.onSurfaceVariant)
                            Text(fmtShort(t.ts), fontSize = 10.sp, color = colors.onSurfaceVariant)
                        }
                        Text(
                            "${if (positive) "+" else ""}${t.delta}",
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = if (positive) colors.success else colors.error,
                        )
                    }
                }
            }
        }

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(error!!, color = colors.error, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        if (success != null) {
            Spacer(Modifier.height(16.dp))
            Text(success!!, color = colors.success, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }

    // Redeem dialog
    if (showRedeem) {
        RedeemDialog(
            onDismiss = { showRedeem = false },
            onRedeem = { code ->
                scope.launch {
                    val result = pricing.redeemCode(uid, code)
                    if (result.isSuccess) {
                        val r = result.getOrNull()
                        success = buildString {
                            append("Redeemed!")
                            if (r != null) {
                                if (r.creditsAdded > 0) append(" +${r.creditsAdded} credits")
                                if (r.coinsAdded > 0) append(" +${r.coinsAdded} coins")
                                if (r.planGranted.isNotBlank()) append(" ${r.planGranted.uppercase()} plan")
                            }
                        }
                        showRedeem = false
                        txs = userData.getTransactionRecords(uid, 5)
                    } else {
                        error = result.exceptionOrNull()?.message ?: "Redeem failed"
                    }
                }
            },
        )
    }
}

private sealed class BuyTarget {
    data class Credits(val pack: CreditPack) : BuyTarget()
    data class Subscription(val plan: SubscriptionPlan) : BuyTarget()
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSanjiColors.current
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, tint = colors.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                label, fontSize = 11.sp, color = colors.onBackground,
                textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun BuyInstructionsModal(
    target: BuyTarget,
    upiId: String,
    upiName: String,
    onClose: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val title = when (target) {
        is BuyTarget.Credits -> target.pack.name
        is BuyTarget.Subscription -> "${target.plan.name} subscription"
    }
    val amount = when (target) {
        is BuyTarget.Credits -> target.pack.priceInr
        is BuyTarget.Subscription -> target.plan.priceInr
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close", tint = colors.onBackground)
            }
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Payment Instructions",
            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.onBackground,
        )
        Spacer(Modifier.height(20.dp))

        Surface(shape = RoundedCornerShape(14.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
    