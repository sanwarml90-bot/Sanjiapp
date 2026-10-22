package com.sanji.bomberpro.ui.giveaway

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.data.Giveaway
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
fun GiveawayScreen(
    uid: String,
    onOpenDetail: (String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val service = remember { GiveawayService() }

    var tab by remember { mutableStateOf(0) }
    var active by remember { mutableStateOf<List<Giveaway>>(emptyList()) }
    var ended by remember { mutableStateOf<List<Giveaway>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

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

    Column(modifier = Modifier.fillMaxSize().background(colors.background).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
            }
            Text(
                "Giveaways",
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = colors.onBackground, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { scope.launch { refresh() } }) {
                Icon(Icons.Default.Refresh, "Refresh", tint = colors.primary)
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
                    Text("🎁", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (tab == 0) "No active giveaways" else "No ended giveaways",
                        fontSize = 14.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list) { gw ->
                GiveawayCard(
                    gw = gw,
                    isEnded = tab == 1,
                    isEntered = gw.entries.containsKey(uid),
                    onClick = { onOpenDetail(gw.id) },
                )
            }
        }
    }
}

@Composable
private fun GiveawayCard(
    gw: Giveaway,
    isEnded: Boolean,
    isEntered: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isEnded) "🏁" else "🎁",
                    fontSize = 18.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    gw.title,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = colors.onBackground,
                    modifier = Modifier.weight(1f),
                )
                if (isEntered && !isEnded) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.success.copy(alpha = 0.15f),
                    ) {
                        Text(
                            "✓ joined",
                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            color = colors.success,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "🏆 ${gw.prize.ifBlank { "Prize" }}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary,
            )
            if (gw.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    gw.description.take(100) + if (gw.description.length > 100) "…" else "",
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "👥 ${gw.entries.size}",
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    "🏅 ${gw.winnersCount} winners",
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (isEnded) "Ended ${fmt(gw.endedAt)}" else "Ends ${fmt(gw.endsAt)}",
                    fontSize = 10.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}