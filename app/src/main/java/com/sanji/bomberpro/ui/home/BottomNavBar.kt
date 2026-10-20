package com.sanji.bomberpro.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.theme.LocalSanjiColors

enum class HomeTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    BLAST("Blast", Icons.Default.Send),
    OTP("OTP", Icons.Default.VpnKey),
    HISTORY("History", Icons.Default.History),
    WALLET("Wallet", Icons.Default.AccountBalanceWallet),
}

@Composable
fun BottomNavBar(
    current: HomeTab,
    onTabSelect: (HomeTab) -> Unit,
) {
    val colors = LocalSanjiColors.current

    NavigationBar(
        containerColor = colors.surface,
        tonalElevation = 0.dp,
    ) {
        HomeTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = current == tab,
                onClick = { onTabSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.primary,
                    selectedTextColor = colors.primary,
                    unselectedIconColor = colors.onSurfaceVariant,
                    unselectedTextColor = colors.onSurfaceVariant,
                    indicatorColor = colors.primary.copy(alpha = 0.15f),
                ),
            )
        }
    }
}