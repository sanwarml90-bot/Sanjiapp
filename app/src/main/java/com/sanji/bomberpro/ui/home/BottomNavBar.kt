package com.sanji.bomberpro.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.theme.LocalSanjiColors
import com.sanji.bomberpro.ui.components.SanjiEmoji
import com.sanji.bomberpro.ui.components.pressScale

enum class HomeTab(
    val label: String,
    val icon: ImageVector,
    val emoji: String,
    val forAdmin: Boolean = true,
    val forUser: Boolean = true,
) {
    HOME("Home", Icons.Default.Home, SanjiEmoji.HOME),
    BLAST("Blast", Icons.Default.Send, SanjiEmoji.BLAST, forAdmin = false),
    ADMIN("Admin", Icons.Default.AdminPanelSettings, SanjiEmoji.ADMIN, forUser = false),
    OTP("OTP", Icons.Default.VpnKey, SanjiEmoji.OTP),
    HISTORY("History", Icons.Default.History, SanjiEmoji.HISTORY),
    WALLET("Wallet", Icons.Default.AccountBalanceWallet, SanjiEmoji.WALLET),
}

@Composable
fun BottomNavBar(
    current: HomeTab,
    isAdmin: Boolean,
    onTabSelect: (HomeTab) -> Unit,
) {
    val colors = LocalSanjiColors.current

    val tabs = HomeTab.entries.filter { tab ->
        if (isAdmin) tab.forAdmin else tab.forUser
    }

    // Floating pill container
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = colors.surface,
            shadowElevation = 8.dp,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { tab ->
                    NavItem(
                        tab = tab,
                        selected = current == tab,
                        onClick = { onTabSelect(tab) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    tab: HomeTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalSanjiColors.current

    val iconColor by animateColorAsState(
        targetValue = if (selected) Color.Black else colors.onSurfaceVariant,
        animationSpec = tween(220),
        label = "navIconColor",
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) colors.primary else Color.Transparent,
        animationSpec = tween(220),
        label = "navBgColor",
    )
    val pillWidth by animateDpAsState(
        targetValue = if (selected) 48.dp else 36.dp,
        animationSpec = tween(220),
        label = "navPillWidth",
    )
    val pillHeight by animateDpAsState(
        targetValue = if (selected) 36.dp else 36.dp,
        animationSpec = tween(220),
        label = "navPillHeight",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.pressScale(),
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(50),
            color = bgColor,
            modifier = Modifier
                .height(pillHeight)
                .size(width = pillWidth, height = pillHeight),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    tab.icon,
                    contentDescription = tab.label,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = tab.label,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}