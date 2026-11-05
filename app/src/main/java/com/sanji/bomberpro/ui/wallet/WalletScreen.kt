package com.sanji.bomberpro.ui.wallet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.sanji.bomberpro.ui.components.AnimatedCounter
import com.sanji.bomberpro.ui.components.SanjiEmoji
import com.sanji.bomberpro.ui.components.fadeSlideIn
import com.sanji.bomberpro.ui.components.pressScale
import com.sanji.bomberpro.ui.payment.PaymentSubmitScreen
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
    userName: String,
    userEmail: String,
    onBack: () -> Unit,
    onOpenTransfer: (() -> Unit)? = null,
    onOpenGift: (() -> Unit)? = null,
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

    LaunchedEffect(Unit) {
        settings = userData.getGlobalSettings()
        packs = pricing.loadCreditPacks()
        plans = pricing.loadSubscriptionPlans()
        txs = userData.getTransactionRecords(uid, 5)
        dailyBonusRemaining = userData.getDailyBonusRemaining(uid)
        loading = false
    }

    LaunchedEffect(uid) {
        UserDocObserver.observeUserDoc(uid).collect { map ->
            credits = (map["credits"] as? Number)?.toInt() ?: 0
            coins = (map["coins"] as? Number)?.toInt() ?: 0
            val sub = map["subscription"] as? Map<*, *>
            subPlan = (sub?.get("plan") as? String) ?: "free"
            subExpiresAt = (sub?.get("expiresAt") as? Number)?.toLong() ?: 0L
        }
    }

    val activeBuy = buyTarget
    if (activeBuy != null) {
        when (activeBuy) {
            is BuyTarget.Credits -> PaymentSubmitScreen(
                uid = uid,
                userName = userName,
                userEmail = userEmail,
                type = "credits",
                packId = activeBuy.pack.id,
                packName = activeBuy.pack.name,
                creditsAmount = activeBuy.pack.credits,
                plan = "",
                amountInr = activeBuy.pack.priceInr,
                upiId = settings.upiId,
                upiName = settings.upiName,
                onClose = { buyTarget = null },
            )
            is BuyTarget.Subscription -> PaymentSubmitScreen(
                uid = uid,
                userName = userName,
                userEmail = userEmail,
                type = "subscription",
                packId = activeBuy.plan.id,
                packName = activeBuy.plan.name,
                creditsAmount = 0,
                plan = activeBuy.plan.id,
                amountInr = activeBuy.plan.priceInr,
                upiId = settings.upiId,
                upiName = settings.upiName,
                onClose = { buyTarget = null },
            )
        }
        return
    }

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

        // Premium balance card
        BalanceCard(
            credits = credits,
            coins = coins,
            modifier = Modifier.fillMaxWidth().fadeSlideIn(delayMs = 0),
        )

        Spacer(Modifier.height(16.dp))

        // Action chips
        Row(
            modifier = Modifier.fillMaxWidth().fadeSlideIn(delayMs = 80),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(Icons.Default.ShoppingCart, SanjiEmoji.CREDITS, "Buy", {
                if (packs.isNotEmpty()) buyTarget = BuyTarget.Credits(packs.first())
                else error = "No packs available"
            }, Modifier.weight(1f))
            ActionButton(Icons.Default.CheckCircle, SanjiEmoji.STAR, "Redeem", { showRedeem = true }, Modifier.weight(1f))
            ActionButton(
                Icons.Default.MonetizationOn,
                SanjiEmoji.GIFT,
                if (dailyBonusRemaining > 0) "Bonus\n${fmtDur(dailyBonusRemaining)}" else "Bonus",
                {
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
                Modifier.weight(1f),
            )
        }

        if (onOpenTransfer != null || onOpenGift != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().fadeSlideIn(delayMs = 140),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onOpenTransfer != null) {
                    ActionButton(Icons.Default.SwapHoriz, SanjiEmoji.TRANSFER, "Transfer", { onOpenTransfer() }, Modifier.weight(1f))
                }
                if (onOpenGift != null) {
                    ActionButton(Icons.Default.CardGiftcard, SanjiEmoji.GIFT, "Gift", { onOpenGift() }, Modifier.weight(1f))
                }
            }
        }

        if (subPlan != "free" && subExpiresAt > System.currentTimeMillis() / 1000L) {
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.primary.copy(alpha = 0.10f),
                modifier = Modifier.fillMaxWidth().fadeSlideIn(delayMs = 180),
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(SanjiEmoji.PREMIUM, fontSize = 18.sp)
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

        if (packs.isNotEmpty()) {
            Text(
                "${SanjiEmoji.CREDITS} Buy Credits",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground,
                modifier = Modifier.fadeSlideIn(delayMs = 220),
            )
            Spacer(Modifier.height(8.dp))
            packs.forEachIndexed { idx, pack ->
                Surface(
                    onClick = { buyTarget = BuyTarget.Credits(pack) },
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surface,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).pressScale()
                        .fadeSlideIn(delayMs = 240 + idx * 40),
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(SanjiEmoji.CREDITS, fontSize = 22.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pack.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                            Text("${pack.credits} credits", fontSize = 11.sp, color = colors.onSurfaceVariant)
                        }
                        Text("₹${pack.priceInr}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (plans.isNotEmpty()) {
            Text(
                "${SanjiEmoji.PREMIUM} Upgrade Subscription",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground,
                modifier = Modifier.fadeSlideIn(delayMs = 300),
            )
            Spacer(Modifier.height(8.dp))
            plans.forEachIndexed { idx, plan ->
                Surface(
                    onClick = { buyTarget = BuyTarget.Subscription(plan) },
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surface,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).pressScale()
                        .fadeSlideIn(delayMs = 320 + idx * 40),
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
                        Text("₹${plan.priceInr}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (txs.isNotEmpty()) {
            Text(
                "${SanjiEmoji.RECEIPT} Recent Transactions",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground,
                modifier = Modifier.fadeSlideIn(delayMs = 380),
            )
            Spacer(Modifier.height(8.dp))
            txs.forEach { t ->
                val isCoin = t.type.contains("coin", true)
                val positive = t.delta >= 0
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surface,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isCoin) SanjiEmoji.COINS else SanjiEmoji.CREDITS, fontSize = 16.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                humanType(t.type),
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                color = colors.onBackground,
                            )
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
private fun BalanceCard(
    credits: Int,
    coins: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSanjiColors.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(colors.primary, colors.secondary)))
                .padding(20.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(SanjiEmoji.WALLET, fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "YOUR BALANCE",
                        fontSize = 10.sp,
                        color = Color.Black.copy(alpha = 0.75f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedCounter(
                        value = credits,
                        style = androidx.compose.ui.text.TextStyle(fontSize = 40.sp),
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "credits",
                        fontSize = 12.sp,
                        color = Color.Black.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.15f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(SanjiEmoji.COINS, fontSize = 12.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "$coins coins",
                            fontSize = 12.sp,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    emoji: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSanjiColors.current
    val scale = remember { Animatable(1f) }
    Surface(
        modifier = modifier.pressScale(),
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = colors.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                fontSize = 11.sp,
                color = colors.onBackground,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun RedeemDialog(
    onDismiss: () -> Unit,
    onRedeem: (String) -> Unit,
) {
    val colors = LocalSanjiColors.current
    var code by remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth().padding(24.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(SanjiEmoji.STAR, fontSize = 18.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Redeem Code",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = colors.onBackground, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = colors.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Enter code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { if (code.isNotBlank()) onRedeem(code) },
                    enabled = code.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(48.dp).pressScale(),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.Black),
                ) { Text("Redeem", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

private fun humanType(type: String): String = when (type) {
    "credit_add" -> "Credits added"
    "credit_add_manual" -> "Credits (manual)"
    "credit_deduct" -> "Credits deducted"
    "blast-reserve" -> "Blast reserved"
    "blast-refund" -> "Blast refund"
    "coin_add" -> "Coins added"
    "coin_add_manual" -> "Coins (manual)"
    "coin_deduct" -> "Coins deducted"
    "daily_bonus" -> "Daily bonus"
    "referral_bonus" -> "Referral bonus"
    "redeem_credit" -> "Redeem credit"
    "redeem_coin" -> "Redeem coin"
    "credit_transfer_in" -> "Transfer in"
    "credit_transfer_out" -> "Transfer out"
    "coin_transfer_in" -> "Coins in"
    "coin_transfer_out" -> "Coins out"
    "coin_gift_in" -> "Gift received"
    "coin_gift_out" -> "Gift sent"
    else -> type.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun fmtDur(seconds: Long): String {
    if (seconds <= 0) return "ready"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}