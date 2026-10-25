package com.sanji.bomberpro.ui.refer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.services.ReferralLeaderboardService
import com.sanji.bomberpro.services.Referrer
import com.sanji.bomberpro.services.UserDataService
import com.sanji.bomberpro.services.UserDocObserver
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch

private const val APP_DOWNLOAD_URL = "https://github.com/Sanjiapp/sanji-bomber-pro/releases/latest"

@Composable
fun ReferScreen(
    uid: String,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val userData = remember { UserDataService() }
    val leaderboardService = remember { ReferralLeaderboardService() }

    var referCode by remember { mutableStateOf("") }
    var referredBy by remember { mutableStateOf<String?>(null) }
    var referCount by remember { mutableStateOf(0) }
    var refCredits by remember { mutableStateOf(3) }
    var refCoins by remember { mutableStateOf(1) }
    var inputCode by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var applying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var topReferrers by remember { mutableStateOf<List<Referrer>>(emptyList()) }
    var loadingLeaderboard by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val settings = userData.getGlobalSettings()
        refCredits = settings.refCredits
        refCoins = settings.refCoins
        referCount = userData.getReferralCount(uid)
        loading = false
        try {
            topReferrers = leaderboardService.getTopReferrers(10)
        } catch (_: Exception) {}
        loadingLeaderboard = false
    }

    LaunchedEffect(uid) {
        UserDocObserver.observeUserDoc(uid).collect { map ->
            referCode = map["referCode"] as? String ?: ""
            referredBy = map["referredBy"] as? String
        }
    }

    fun copyToClipboard(label: String, value: String) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(label, value))
            success = "Copied"
        } catch (_: Exception) { error = "Copy failed" }
    }

    fun buildShareText(): String {
        return "Join Sanji Bomber Pro! 🔥\n\n" +
                "Use my referral code: $referCode\n" +
                "You get +$refCredits credits + $refCoins coins\n\n" +
                "Download: $APP_DOWNLOAD_URL"
    }

    fun shareToWhatsApp() {
        try {
            val text = Uri.encode(buildShareText())
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=$text"))
            context.startActivity(intent)
        } catch (_: Exception) { error = "WhatsApp not installed" }
    }

    fun shareToTelegram() {
        try {
            val text = Uri.encode(buildShareText())
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/share/url?url=${Uri.encode(APP_DOWNLOAD_URL)}&text=$text"))
            context.startActivity(intent)
        } catch (_: Exception) { error = "Telegram not installed" }
    }

    fun shareGeneric() {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, buildShareText())
            }
            context.startActivity(Intent.createChooser(intent, "Share referral"))
        } catch (_: Exception) { error = "Unable to share" }
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
                "Refer & Earn",
                fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = colors.onBackground, modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(16.dp))

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
            return
        }

        // Hero card
        Surface(
            shape = RoundedCornerShape(24.dp),
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
                    Text(
                        "YOUR REFERRAL CODE",
                        fontSize = 10.sp, color = Color.Black.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            referCode.ifBlank { "—" },
                            fontSize = 26.sp, fontWeight = FontWeight.Black,
                            color = Color.Black, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                        )
                        if (referCode.isNotBlank()) {
                            Surface(
                                onClick = { copyToClipboard("Referral code", referCode) },
                                shape = RoundedCornerShape(50),
                                color = Color.Black.copy(alpha = 0.15f),
                                modifier = Modifier.size(38.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.ContentCopy, "Copy", tint = Color.Black, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Earn $refCredits credits + $refCoins coins per signup",
                        fontSize = 11.sp, color = Color.Black.copy(alpha = 0.75f),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = colors.surface, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Total Referred", fontSize = 11.sp, color = colors.onSurfaceVariant)
                    Text(
                        "$referCount", fontSize = 22.sp, fontWeight = FontWeight.Black,
                        color = colors.primary,
                    )
                }
            }
            Surface(shape = RoundedCornerShape(14.dp), color = colors.surface, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Total Earned", fontSize = 11.sp, color = colors.onSurfaceVariant)
                    Text(
                        "${referCount * refCredits}💰",
                        fontSize = 22.sp, fontWeight = FontWeight.Black,
                        color = colors.success,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Top Referrers Leaderboard
        Surface(shape = RoundedCornerShape(14.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🏆", fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Top Referrers",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = colors.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "All time",
                        fontSize = 10.sp, color = colors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (loadingLeaderboard) {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.primary)
                    }
                } else if (topReferrers.isEmpty()) {
                    Text(
                        "No referrals yet — be the first!",
                        fontSize = 12.sp, color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                } else {
                    val medals = listOf("🥇", "🥈", "🥉")
                    topReferrers.forEachIndexed { idx, r ->
                        val isMe = r.uid == uid
                        val medal = if (idx < 3) medals[idx] else "#${idx + 1}"
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isMe) colors.primary.copy(alpha = 0.12f) else colors.background,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    medal,
                                    fontSize = 16.sp,
                                    modifier = Modifier.width(32.dp),
                                )
                                Text(
                                    r.name + if (isMe) " (you)" else "",
                                    fontSize = 12.sp,
                                    fontWeight = if (isMe) FontWeight.Bold else FontWeight.SemiBold,
                                    color = colors.onBackground,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${r.count} refs",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primary,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Share buttons
        Text(
            "Share Invite",
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground,
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShareButton("WhatsApp", "🟢", onClick = { shareToWhatsApp() }, modifier = Modifier.weight(1f))
            ShareButton("Telegram", "🔵", onClick = { shareToTelegram() }, modifier = Modifier.weight(1f))
            ShareButton("More", "↗️", onClick = { shareGeneric() }, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(24.dp))

        // Apply referral code
        if (referredBy.isNullOrBlank()) {
            Surface(shape = RoundedCornerShape(14.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Got a referral code?",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Enter your friend's code to get +$refCredits credits and +$refCoins coins.",
                        fontSize = 11.sp, color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = { inputCode = it.uppercase() },
                        label = { Text("Referral code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (inputCode.isBlank()) return@Button
                            applying = true
                            error = null
                            success = null
                            scope.launch {
                                val result = userData.applyReferralCode(uid, inputCode)
                                if (result.isSuccess) {
                                    val r = result.getOrNull()
                                    success = "Redeemed! +${r?.creditsAwarded} credits + ${r?.coinsAwarded} coins"
                                    inputCode = ""
                                    try {
                                        topReferrers = leaderboardService.getTopReferrers(10)
                                    } catch (_: Exception) {}
                                } else {
                                    error = result.exceptionOrNull()?.message ?: "Failed"
                                }
                                applying = false
                            }
                        },
                        enabled = !applying && inputCode.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.Black),
                    ) {
                        if (applying) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
                        } else {
                            Text("Apply Code", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            Surface(shape = RoundedCornerShape(14.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✅", fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Already referred", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                            Text(
                                "You were referred by a friend",
                                fontSize = 11.sp, color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // How it works
        Surface(shape = RoundedCornerShape(14.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("How it works", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                Spacer(Modifier.height(8.dp))
                HowItWorksStep("1", "Share your code with friends")
                HowItWorksStep("2", "They sign up with your code")
                HowItWorksStep("3", "Both of you earn rewards instantly")
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

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ShareButton(
    label: String,
    emoji: String,
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
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 11.sp, color = colors.onBackground, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HowItWorksStep(step: String, text: String) {
    val colors = LocalSanjiColors.current
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = colors.primary.copy(alpha = 0.15f),
            modifier = Modifier.size(24.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(step, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 12.sp, color = colors.onBackground)
    }
}