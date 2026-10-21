package com.sanji.bomberpro.ui.home

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
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseUser
import com.sanji.bomberpro.data.AuthManager
import com.sanji.bomberpro.data.UserProfile
import com.sanji.bomberpro.data.UserRole
import com.sanji.bomberpro.navigation.Routes
import com.sanji.bomberpro.services.UserDocObserver
import com.sanji.bomberpro.theme.LocalSanjiColors
import com.sanji.bomberpro.ui.history.HistoryScreen
import com.sanji.bomberpro.ui.otp.OtpScreen
import com.sanji.bomberpro.ui.wallet.WalletScreen

@Composable
fun HomeScreen(
    user: FirebaseUser,
    role: UserRole,
    profile: UserProfile?,
    authManager: AuthManager,
    onNavigate: (String) -> Unit,
) {
    val colors = LocalSanjiColors.current
    var currentTab by remember { mutableStateOf(HomeTab.HOME) }

    var userMap by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    LaunchedEffect(user.uid) {
        UserDocObserver.observeUserDoc(user.uid).collect { userMap = it }
    }

    val liveCredits = (userMap["credits"] as? Number)?.toInt() ?: (profile?.credits ?: 0)
    val liveCoins = (userMap["coins"] as? Number)?.toInt() ?: (profile?.coins ?: 0)
    val liveTotalSms = (userMap["totalSms"] as? Number)?.toInt() ?: (profile?.totalSms ?: 0)
    val liveTotalOtps = (userMap["totalOtps"] as? Number)?.toInt() ?: (profile?.totalOtps ?: 0)

    Scaffold(
        containerColor = colors.background,
        bottomBar = {
            BottomNavBar(current = currentTab, onTabSelect = { currentTab = it })
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentTab) {
                HomeTab.HOME -> HomeTabContent(
                    user = user,
                    role = role,
                    profile = profile,
                    liveCredits = liveCredits,
                    liveCoins = liveCoins,
                    liveTotalSms = liveTotalSms,
                    liveTotalOtps = liveTotalOtps,
                    onStartBlast = { onNavigate(Routes.SMS_BLAST) },
                    onOpenWallet = { currentTab = HomeTab.WALLET },
                    onOpenHistory = { currentTab = HomeTab.HISTORY },
                    onOpenRefer = { onNavigate(Routes.REFER) },
                )
                HomeTab.BLAST -> PlaceholderScreen("SMS Blast", "Tap START on home")
                HomeTab.OTP -> OtpScreen(
                    uid = user.uid,
                    userName = profile?.displayName ?: (user.displayName ?: "User"),
                    userEmail = profile?.email ?: (user.email ?: ""),
                    isAdmin = role.priority >= UserRole.ADMIN.priority,
                    onBack = { currentTab = HomeTab.HOME },
                )
                HomeTab.HISTORY -> HistoryScreen(
                    uid = user.uid,
                    onBack = { currentTab = HomeTab.HOME },
                )
                HomeTab.WALLET -> WalletScreen(
                    uid = user.uid,
                    onBack = { currentTab = HomeTab.HOME },
                )
            }
        }
    }
}

@Composable
private fun HomeTabContent(
    user: FirebaseUser,
    role: UserRole,
    profile: UserProfile?,
    liveCredits: Int,
    liveCoins: Int,
    liveTotalSms: Int,
    liveTotalOtps: Int,
    onStartBlast: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenRefer: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (profile?.photoUrl?.isNotBlank() == true) {
                AsyncImage(
                    model = profile.photoUrl,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(50)).background(colors.surface),
                )
            } else {
                Box(
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(50))
                        .background(colors.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (profile?.displayName?.firstOrNull() ?: 'U').toString(),
                        fontWeight = FontWeight.Bold, color = colors.primary,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    profile?.displayName ?: user.displayName ?: "User",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground,
                )
                Text(
                    role.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                    fontSize = 11.sp, color = colors.primary,
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colors.primary.copy(alpha = 0.15f),
                onClick = onOpenWallet,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.MonetizationOn, null, modifier = Modifier.size(16.dp), tint = colors.primary)
                    Spacer(Modifier.width(6.dp))
                    Text("$liveCredits", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Surface(
            onClick = onStartBlast,
            modifier = Modifier.fillMaxWidth().height(130.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.Transparent,
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Brush.linearGradient(colors = listOf(colors.primary, colors.secondary))),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.RocketLaunch, null, modifier = Modifier.size(38.dp), tint = Color.Black)
                    Spacer(Modifier.height(8.dp))
                    Text("START SMS BLAST", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    Text("Tap to launch", fontSize = 11.sp, color = Color.Black.copy(alpha = 0.7f))
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Icons.Default.Send, "Total SMS", "$liveTotalSms", Modifier.weight(1f))
            StatCard(Icons.Default.VpnKey, "OTPs", "$liveTotalOtps", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Icons.Default.MonetizationOn, "Credits", "$liveCredits", Modifier.weight(1f))
            StatCard(Icons.Default.Toll, "Coins", "$liveCoins", Modifier.weight(1f))
        }

        Spacer(Modifier.height(24.dp))

        Text("Quick Access", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAccessChip(Icons.Default.History, "History", onOpenHistory, Modifier.weight(1f))
            QuickAccessChip(Icons.Default.CardGiftcard, "Refer", onOpenRefer, Modifier.weight(1f))
            QuickAccessChip(Icons.Default.Settings, "Settings", { }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalSanjiColors.current
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = colors.surface) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(icon, null, tint = colors.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(10.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
            Text(label, fontSize = 11.sp, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuickAccessChip(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSanjiColors.current
    Surface(modifier = modifier, onClick = onClick, shape = RoundedCornerShape(14.dp), color = colors.surface) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, tint = colors.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 11.sp, color = colors.onBackground)
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, subtitle: String) {
    val colors = LocalSanjiColors.current
    Box(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, fontSize = 13.sp, color = colors.onSurfaceVariant)
        }
    }
}