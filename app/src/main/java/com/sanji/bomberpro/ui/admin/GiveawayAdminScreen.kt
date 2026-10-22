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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.data.Giveaway
import com.sanji.bomberpro.data.LeaderboardRow
import com.sanji.bomberpro.services.GiveawayService
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FMT = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
private fun fmt(ts: Long): String =
    try { FMT.format(Date(ts * 1000L)) } catch (_: Exception) { "—" }

@Composable
fun GiveawayAdminScreen(
    adminUid: String,
    onCreate: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val service = remember { GiveawayService() }

    var tab by remember { mutableStateOf(0) }
    var active by remember { mutableStateOf<List<Giveaway>>(emptyList()) }
    var ended by remember { mutableStateOf<List<Giveaway>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<Giveaway?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var showEndConfirm by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        loading = true
        try {
            active = service.listActive()
            ended = service.listEnded()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(error) {
        if (error != null) { kotlinx.coroutines.delay(4000); error = null }
    }
    LaunchedEffect(success) {
        if (success != null) { kotlinx.coroutines.delay(4000); success = null }
    }

    if (selected != null) {
        GiveawayAdminDetail(
            gw = selected!!,
            adminUid = adminUid,
            service = service,
            onBack = {
                selected = null
                scope.launch { refresh() }
            },
            onUpdate = {
                scope.launch {
                    val fresh = service.getGiveaway(selected!!.id)
                    if (fresh != null) selected = fresh
                }
            },
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.background).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
            }
            Text(
                "Manage Giveaways",
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = colors.onBackground, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { scope.launch { refresh() } }) {
                Icon(Icons.Default.Refresh, "Refresh", tint = colors.primary)
            }
            IconButton(onClick = onCreate) {
                Icon(Icons.Default.Add, "New", tint = colors.primary)
            }
        }

        Spacer(Modifier.height(8.dp))

        ScrollableTabRow(
            selectedTabIndex = tab,
            containerColor = Color.Transparent,
            contentColor = colors.primary,
            edgePadding = 0.dp,
        ) {
            listOf("Active (${active.size})", "Ended (${ended.size})").forEachIndexed { i, label ->
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

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
            return
        }

        val list = if (tab == 0) active else ended
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (tab == 0) "No active giveaways" else "No ended giveaways",
                        fontSize = 14.sp, color = colors.onSurfaceVariant,
                    )
                    if (tab == 0) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onCreate,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primary, contentColor = Color.Black,
                            ),
                        ) { Text("Create Giveaway") }
                    }
                }
            }
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list) { gw ->
                AdminGwCard(
                    gw = gw,
                    isActive = tab == 0,
                    onClick = { selected = gw },
                    onEnd = { showEndConfirm = gw.id },
                )
            }
        }
    }

    if (showEndConfirm != null) {
        AlertDialog(
            onDismissRequest = { showEndConfirm = null },
            confirmButton = {
                TextButton(onClick = {
                    val gwId = showEndConfirm!!
                    showEndConfirm = null
                    scope.launch {
                        val res = service.endGiveaway(gwId, adminUid, requestChannelPost = true)
                        if (res.isSuccess) {
                            success = "Giveaway ended"
                            refresh()
                        } else {
                            error = res.exceptionOrNull()?.message ?: "Failed"
                        }
                    }
                }) { Text("End Now", color = colors.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirm = null }) {
                    Text("Cancel", color = colors.onSurfaceVariant)
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.onBackground,
            textContentColor = colors.onBackground,
            title = { Text("End Giveaway?") },
            text = {
                Text(
                    "Winners will be picked now. Prizes will be distributed immediately.",
                    fontSize = 12.sp,
                )
            },
        )
    }

    if (error != null || success != null) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (error != null) colors.error.copy(alpha = 0.95f) else colors.success.copy(alpha = 0.95f),
            ) {
                Text(
                    error ?: success ?: "",
                    fontSize = 12.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AdminGwCard(
    gw: Giveaway,
    isActive: Boolean,
    onClick: () -> Unit,
    onEnd: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(gw.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, modifier = Modifier.weight(1f))
                if (isActive) {
                    IconButton(onClick = onEnd) {
                        Icon(Icons.Default.Stop, "End", tint = colors.error, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Text("🏆 ${gw.prize}", fontSize = 12.sp, color = colors.primary)
            Text(
                "👥 ${gw.entries.size} · 🏅 ${gw.winnersCount} · ${if (isActive) "Ends" else "Ended"} ${fmt(if (isActive) gw.endsAt else gw.endedAt)}",
                fontSize = 10.sp, color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GiveawayAdminDetail(
    gw: Giveaway,
    adminUid: String,
    service: GiveawayService,
    onBack: () -> Unit,
    onUpdate: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()

    var leaderboard by remember { mutableStateOf<List<LeaderboardRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var showVote by remember { mutableStateOf<String?>(null) }
    var voteAmount by remember { mutableStateOf("1") }

    LaunchedEffect(gw.id) {
        loading = true
        try {
            leaderboard = service.computeLeaderboard(gw)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(error) {
        if (error != null) { kotlinx.coroutines.delay(4000); error = null }
    }
    LaunchedEffect(success) {
        if (success != null) { kotlinx.coroutines.delay(4000); success = null }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
            }
            Text(
                gw.title,
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = colors.onBackground, modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        Surface(shape = RoundedCornerShape(14.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                InfoRow("Status", gw.status)
                InfoRow("Prize", gw.prize)
                InfoRow("Ends", fmt(gw.endsAt))
                InfoRow("Participants", "${gw.entries.size}")
                InfoRow("Winners", "${gw.winnersCount}")
                InfoRow(
                    "Method",
                    when (gw.winnerMethod) {
                        "votes_only" -> "Votes"
                        "refs_only" -> "Refs"
                        else -> "Votes + refs"
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
        } else if (leaderboard.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                Text("No entries yet", fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
        } else {
            Text("📊 Leaderboard", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
            Spacer(Modifier.height(8.dp))
            leaderboard.take(30).forEach { row ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.surface,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (row.rank) {
                                1 -> "🥇"
                                2 -> "🥈"
                                3 -> "🥉"
                                else -> "#${row.rank}"
                            },
                            fontSize = 14.sp, modifier = Modifier.width(36.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                            Text(
                                "🗳 ${row.votes}v · 👥 ${row.refs}r · score ${"%.1f".format(row.score)}",
                                fontSize = 10.sp, color = colors.onSurfaceVariant,
                            )
                            Text(
                                "uid ${row.uid}",
                                fontSize = 9.sp, color = colors.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        TextButton(onClick = { showVote = row.uid }) {
                            Text("votes", color = colors.primary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showVote != null) {
        AlertDialog(
            onDismissRequest = { showVote = null },
            confirmButton = {
                TextButton(onClick = {
                    val amt = voteAmount.toIntOrNull() ?: 0
                    if (amt == 0) { error = "Amount must not be 0"; return@TextButton }
                    val target = showVote!!
                    showVote = null
                    scope.launch {
                        val res = service.adjustVotes(
                            gwId = gw.id,
                            adminUid = adminUid,
                            targetUid = target,
                            delta = amt,
                            reason = "admin-adjust",
                        )
                        if (res.isSuccess) {
                            success = if (amt > 0) "+$amt votes" else "$amt votes"
                            leaderboard = service.computeLeaderboard(gw)
                            onUpdate()
                        } else {
                            error = res.exceptionOrNull()?.message ?: "Failed"
                        }
                    }
                }) { Text("Apply", color = colors.primary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showVote = null }) {
                    Text("Cancel", color = colors.onSurfaceVariant)
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.onBackground,
            textContentColor = colors.onBackground,
            title = { Text("Adjust Votes") },
            text = {
                Column {
                    Text(
                        "Enter positive for +votes, negative for -votes",
                        fontSize = 11.sp, color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = voteAmount,
                        onValueChange = { voteAmount = it },
                        label = { Text("Delta") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }

    if (error != null || success != null) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (error != null) colors.error.copy(alpha = 0.95f) else colors.success.copy(alpha = 0.95f),
            ) {
                Text(
                    error ?: success ?: "",
                    fontSize = 12.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}