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
import com.sanji.bomberpro.data.AccountAnalysis
import com.sanji.bomberpro.data.AnalyzedUser
import com.sanji.bomberpro.data.DuplicateGroup
import com.sanji.bomberpro.services.AdminService
import com.sanji.bomberpro.theme.LocalSanjiColors
import com.sanji.bomberpro.ui.components.SanjiEmoji
import kotlinx.coroutines.launch

@Composable
fun AccountAnalysisScreen(
    adminUid: String,
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val service = remember { AdminService() }

    var data by remember { mutableStateOf<AccountAnalysis?>(null) }
    var loading by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf(0) }

    suspend fun refresh() {
        loading = true
        try {
            data = service.analyzeAccounts()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

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
                "Account Analysis",
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = colors.onBackground, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { scope.launch { refresh() } }, enabled = !loading) {
                Icon(Icons.Default.Refresh, "Refresh", tint = colors.primary)
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "Inactive = 30+ days idle · Dead = 0 sends + 7+ days old · Duplicate = same device, multiple UIDs",
            fontSize = 11.sp, color = colors.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        if (loading || data == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = colors.primary)
                    Spacer(Modifier.height(10.dp))
                    Text("Analyzing accounts…", fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
            }
            return
        }

        val d = data!!

        // Summary chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryChip(SanjiEmoji.USERS, "${d.totalUsers}", "Total", colors.primary, Modifier.weight(1f))
            SummaryChip(SanjiEmoji.CLOCK, "${d.inactive.size}", "Inactive", colors.warning, Modifier.weight(1f))
            SummaryChip(SanjiEmoji.BANNED, "${d.dead.size}", "Dead", colors.error, Modifier.weight(1f))
            SummaryChip(SanjiEmoji.USERS, "${d.duplicateGroups.size}", "Dup", colors.secondary, Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))

        ScrollableTabRow(
            selectedTabIndex = tab,
            containerColor = Color.Transparent,
            contentColor = colors.primary,
            edgePadding = 0.dp,
        ) {
            listOf(
                "Inactive (${d.inactive.size})",
                "Dead (${d.dead.size})",
                "Duplicates (${d.duplicateGroups.size})",
            ).forEachIndexed { i, label ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = {
                        Text(
                            label,
                            fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp,
                        )
                    },
                    selectedContentColor = colors.primary,
                    unselectedContentColor = colors.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        when (tab) {
            0 -> {
                if (d.inactive.isEmpty()) {
                    EmptyState("No inactive accounts", SanjiEmoji.SUCCESS)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(d.inactive) { u ->
                            AnalyzedUserCard(u, onOpenUser, subtitle = "Idle ${u.daysInactive}d")
                        }
                    }
                }
            }
            1 -> {
                if (d.dead.isEmpty()) {
                    EmptyState("No dead accounts", SanjiEmoji.SUCCESS)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(d.dead) { u ->
                            AnalyzedUserCard(u, onOpenUser, subtitle = "Age ${u.daysOld}d · 0 sends")
                        }
                    }
                }
            }
            2 -> {
                if (d.duplicateGroups.isEmpty()) {
                    EmptyState("No duplicates", SanjiEmoji.SUCCESS)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(d.duplicateGroups) { group ->
                            DuplicateGroupCard(group, onOpenUser)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(
    emoji: String,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSanjiColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(emoji, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Black, color = color)
            Text(label, fontSize = 9.sp, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun AnalyzedUserCard(
    user: AnalyzedUser,
    onOpenUser: (String) -> Unit,
    subtitle: String,
) {
    val colors = LocalSanjiColors.current
    Surface(
        onClick = { onOpenUser(user.uid) },
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user.displayName.ifBlank { "User" },
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = colors.onBackground,
                    )
                    if (user.banned) {
                        Spacer(Modifier.width(6.dp))
                        Text(SanjiEmoji.BANNED, fontSize = 12.sp)
                    }
                }
                if (user.email.isNotBlank()) {
                    Text(user.email, fontSize = 10.sp, color = colors.onSurfaceVariant, maxLines = 1)
                }
                Text(
                    "UID: ${user.uid}",
                    fontSize = 9.sp, color = colors.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace, maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    fontSize = 10.sp, color = colors.primary, fontWeight = FontWeight.SemiBold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${SanjiEmoji.CREDITS} ${user.credits}",
                    fontSize = 10.sp, color = colors.onSurfaceVariant,
                )
                Text(
                    "${SanjiEmoji.COINS} ${user.coins}",
                    fontSize = 10.sp, color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    onOpenUser: (String) -> Unit,
) {
    val colors = LocalSanjiColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(SanjiEmoji.WARNING, fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${group.users.size} accounts on 1 device",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = colors.error,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "fp: ${group.fingerprint.take(16)}…",
                fontSize = 9.sp, color = colors.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(8.dp))
            group.users.forEach { u ->
                Surface(
                    onClick = { onOpenUser(u.uid) },
                    shape = RoundedCornerShape(8.dp),
                    color = colors.background,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                u.displayName.ifBlank { "User" },
                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                color = colors.onBackground,
                            )
                            Text(
                                u.email.ifBlank { u.uid },
                                fontSize = 9.sp, color = colors.onSurfaceVariant, maxLines = 1,
                            )
                        }
                        Text(
                            "${u.totalSms} SMS",
                            fontSize = 9.sp, color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(text: String, emoji: String) {
    val colors = LocalSanjiColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 42.sp)
            Spacer(Modifier.height(8.dp))
            Text(text, fontSize = 13.sp, color = colors.onSurfaceVariant)
        }
    }
}