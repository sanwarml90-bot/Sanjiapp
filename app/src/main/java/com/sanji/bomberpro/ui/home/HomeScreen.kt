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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.WorkspacePremium
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
import com.sanji.bomberpro.services.NotificationsService
import com.sanji.bomberpro.services.UserDocObserver
import com.sanji.bomberpro.theme.LocalSanjiColors
import com.sanji.bomberpro.ui.admin.AdminScreen
import com.sanji.bomberpro.ui.blast.SmsBlastScreen
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

    val isAdmin = role.priority >= UserRole.ADMIN.priority
    val isOwner = role.priority >= UserRole.SUPER_ADMIN.priority

    var userMap by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    LaunchedEffect(user.uid) {
        UserDocObserver.observeUserDoc(user.uid).collect { userMap = it }
    }

    var unreadNotifications by remember { mutableStateOf(0) }
    LaunchedEffect(user.uid) {
        NotificationsService().unreadCountFlow(user.uid).collect { unreadNotifications = it }
    }

    val liveCredits = (userMap["credits"] as? Number)?.toInt() ?: (profile?.credits ?: 0)
    val liveCoins = (userMap["coins"] as? Number)?.toInt() ?: (profile?.coins ?: 0)
    val liveTotalSms = (userMap["totalSms"] as? Number)?.toInt() ?: (profile?.totalSms ?: 0)
    val liveTotalOtps = (userMap["totalOtps"] as? Number)?.toInt() ?: (profile?.totalOtps ?: 0)
    val livePremium = (userMap["premium"] as? Boolean) ?: (profile?.premium ?: false)
    val liveBanned = (userMap["banned"] as? Boolean) ?: (profile?.banned ?: false)
    val liveBanReason = (userMap["banReason"] as? String) ?: (profile?.banReason ?: "")

    val userName = profile?.displayName ?: (user.displayName ?: "User")
    val userEmail = profile?.email ?: (user.email ?: "")

    Scaffold(
        containerColor = colors.background,
        bottomBar = {
            BottomNavBar(
                current = currentTab,
                isAdmin = isAdmin,
                onTabSelect = { currentTab = it },
            )
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
                    livePremium = livePremium,
                    liveBanned = liveBanned,
                    liveBanReason = liveBanReason,
                    unreadNotifications = unreadNotifications,
                    onStartBlast = { currentTab = HomeTab.BLAST },
                    onOpenWallet = { currentTab = HomeTab.WALLET },
                    onOpenHistory = { currentTab = HomeTab.HISTORY },
                    onOpenOtp = { currentTab = HomeTab.OTP },
                    onOpenRefer = { onNavigate(Routes.REFER) },
                    onOpenNotifications = { onNavigate(Routes.NOTIFICATIONS) },
                    onOpenTransfer = { onNavigate(Routes.TRANSFER) },
                    onOpenGift = { onNavigate(Routes.GIFT) },
                    onOpenTemplates = { onNavigate(Routes.TEMPLATES) },
                    onOpenGiveaways = { onNavigate(Routes.GIVEAWAYS) },
                    onOpenAnnouncements = { onNavigate(Routes.ANNOUNCEMENTS) },
                    onOpenSupport = { onNavigate(Routes.SUPPORT) },
                    onOpenSettings = { onNavigate(Routes.USER_SETTINGS) },
                    onOpenSchedule = { onNavigate(Routes.SCHEDULE_BLAST) },
                    onOpenAppeal = { onNavigate(Routes.APPEAL) },
                )

                HomeTab.BLAST -> SmsBlastScreen(
                    authManager = authManager,
                    onBack = { currentTab = HomeTab.HOME },
                    onOpenBulkMode = { onNavigate(Routes.BULK_BLAST) },
                )

                HomeTab.ADMIN -> {
                    if (isAdmin) {
                        AdminScreen(
                            adminUid = user.uid,
                            adminEmail = userEmail,
                            adminName = userName,
                            isOwner = isOwner,
                            onExit = { currentTab = HomeTab.HOME },
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(colors.background),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Not authorized", color = colors.onSurfaceVariant)
                        }
                    }
                }

                HomeTab.OTP -> OtpScreen(
                    uid = user.uid,
                    userName = userName,
                    userEmail = userEmail,
                    isAdmin = isAdmin,
                    onBack = { currentTab = HomeTab.HOME },
                )

                HomeTab.HISTORY -> HistoryScreen(
                    uid = user.uid,
                    onBack = { currentTab = HomeTab.HOME },
                )

                HomeTab.WALLET -> WalletScreen(
                    uid = user.uid,
                    userName = userName,
                    userEmail = userEmail,
                    onBack = { currentTab = HomeTab.HOME },
                    onOpenTransfer = { onNavigate(Routes.TRANSFER) },
                    onOpenGift = { onNavigate(Routes.GIFT) },
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
    livePremium: Boolean,
    liveBanned: Boolean,
    liveBanReason: String,
    unreadNotifications: Int,
    onStartBlast: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenOtp: () -> Unit,
    onOpenRefer: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenGift: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenGiveaways: () -> Unit,
    onOpenAnnouncements: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenAppeal: () -> Unit,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile?.displayName ?: user.displayName ?: "User",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground,
                    )
                    if (livePremium) {
                        Spacer(Modifier.width(6.dp))
                        Text("💎", fontSize = 13.sp)
                    }
                }
                Text(
                    "@${profile?.username?.ifBlank { "user" } ?: "user"} · ${role.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }}",
                    fontSize = 11.sp, color = colors.primary,
                )
            }

            Box(modifier = Modifier.size(42.dp)) {
                Surface(
                    onClick = onOpenNotifications,
                    shape = CircleShape,
                    color = colors.surface,
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Notifications, "Notifications",
                            tint = colors.onBackground, modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (unreadNotifications > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(colors.error),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (unreadNotifications > 9) "9+" else "$unreadNotifications",
                            fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                onClick = onOpenSettings,
                shape = CircleShape,
                color = colors.surface,
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Settings, "Settings",
                        tint = colors.onBackground, modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        if (liveBanned) {
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.error.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🚫", fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Account banned",
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.error,
                            )
                            Text(
                                liveBanReason.ifBlank { "Contact admin or appeal" },
                                fontSize = 11.sp, color = colors.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        onClick = onOpenAppeal,
                        shape = RoundedCornerShape(10.dp),
                        color = colors.error,
                    ) {
                        Text(
                            "Appeal Ban",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Surface(
            onClick = onStartBlast,
            modifier = Modifier.fillMaxWidth().height(120.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.Transparent,
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Brush.linearGradient(colors = listOf(colors.primary, colors.secondary))),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.RocketLaunch, null, modifier = Modifier.size(36.dp), tint = Color.Black)
                    Spacer(Modifier.height(8.dp))
                    Text("START SMS BLAST", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    Text("Tap to launch", fontSize = 11.sp, color = Color.Black.copy(alpha = 0.7f))
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Icons.Default.Send, "Total SMS", "$liveTotalSms", Modifier.weight(1f))
            StatCard(Icons.Default.VpnKey, "OTPs", "$liveTotalOtps", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Icons.Default.MonetizationOn, "Credits", "$liveCredits", Modifier.weight(1f), onClick = onOpenWallet)
            StatCard(Icons.Default.Toll, "Coins", "$liveCoins", Modifier.weight(1f), onClick = onOpenWallet)
        }

        Spacer(Modifier.height(20.dp))

        Text("Quick Access", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAccessChip(Icons.Default.History, "History", onOpenHistory, Modifier.weight(1f))
            QuickAccessChip(Icons.Default.CardGiftcard, "Refer", onOpenRefer, Modifier.weight(1f))
            QuickAccessChip(Icons.Default.VpnKey, "OTP", onOpenOtp, Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAccessChip(Icons.Default.SwapHoriz, "Transfer", onOpenTransfer, Modifier.weight(1f))
            QuickAccessChip(Icons.Default.CardGiftcard, "Gift", onOpenGift, Modifier.weight(1f))
            QuickAccessChip(Icons.Default.WorkspacePremium, "Templates", onOpenTemplates, Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAccessChip(Icons.Default.WorkspacePremium, "Giveaways", onOpenGiveaways, Modifier.weight(1f))
            QuickAccessChip(Icons.Default.Notifications, "Announce", onOpenAnnouncements, Modifier.weight(1f))
            QuickAccessChip(Icons.Default.MonetizationOn, "Schedule", onOpenSchedule, Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAccessChip(Icons.Default.SupportAgent, "Support", onOpenSupport, Modifier.weight(1f))
            // Keeps row layout stable — two spacers fill remaining slots
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalSanjiColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        onClick = onClick ?: {},
    ) {
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