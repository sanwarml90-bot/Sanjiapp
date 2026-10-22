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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.sanji.bomberpro.FirebaseConfig
import com.sanji.bomberpro.data.Giveaway
import com.sanji.bomberpro.data.LeaderboardRow
import com.sanji.bomberpro.services.GiveawayService
import com.sanji.bomberpro.services.UserDataService
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FMT = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
private fun fmt(ts: Long): String =
    try { FMT.format(Date(ts * 1000L)) } catch (_: Exception) { "—" }

@Composable
fun GiveawayDetailScreen(
    gwId: String,
    uid: String,
    isForceJoinVerified: Boolean,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val service = remember { GiveawayService() }
    val userData = remember { UserDataService() }

    var gw by remember { mutableStateOf<Giveaway?>(null) }
    var leaderboard by remember { mutableStateOf<List<LeaderboardRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var joining by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        loading = true
        try {
            gw = service.getGiveaway(gwId)
            leaderboard = gw?.let { service.computeLeaderboard(it) } ?: emptyList()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(gwId) { refresh() }

    LaunchedEffect(error) {
        if (error != null) { kotlinx.coroutines.delay(4000); error = null }
    }
    LaunchedEffect(success) {
        if (success != null) { kotlinx.coroutines.delay(4000); success = null }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
                }
                Text(
                    "Giveaway",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = colors.onBackground, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { scope.launch { refresh() } }) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = colors.primary)
                }
            }

            Spacer(Modifier.height(8.dp))

            if (loading || gw == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primary)
                }
                return@Column
            }

            val g = gw!!
            val entered = g.entries.containsKey(uid)
            val isEnded = g.status == "ended"
            val now = System.currentTimeMillis() / 1000L
            val isExpired = now > g.endsAt

            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            ) {
                Surface(shape = RoundedCornerShape(16.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(if (isEnded) "🏁" else "🎁", fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            g.title,
                            fontSize = 20.sp, fontWeight = FontWeight.Black, color = colors.onBackground,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("🏆 ${g.prize}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
                        if (g.description.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(g.description, fontSize = 12.sp, color = colors.onSurfaceVariant)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Surface(shape = RoundedCornerShape(14.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        DetailInfoRow("Status", if (isEnded) "Ended" else if (isExpired) "Expired" else "Active")
                        DetailInfoRow("Ends", fmt(g.endsAt))
                        if (isEnded) DetailInfoRow("Ended", fmt(g.endedAt))
                        DetailInfoRow("Participants", "${g.entries.size}")
                        DetailInfoRow("Winners", "${g.winnersCount}")
                        DetailInfoRow(
                            "Method",
                            when (g.winnerMethod) {
                                "votes_only" -> "Votes only"
                                "refs_only" -> "Referrals only"
                                else -> "Votes + refs"
                            }
                        )
                    }
                }

                if (!isEnded && (
                            g.requirements.forceJoin ||
                                    g.requirements.minRefs > 0 ||
                                    g.requirements.minCredits > 0 ||
                                    g.requirements.minDays > 0
                            )
                ) {
                    Spacer(Modifier.height(12.dp))
                    Surface(shape = RoundedCornerShape(14.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "Requirements",
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onBackground,
                            )
                            Spacer(Modifier.height(6.dp))
                            if (g.requirements.forceJoin) DetailBullet("Join required channels")
                            if (g.requirements.minRefs > 0) DetailBullet("Min ${g.requirements.minRefs} referrals")
                            if (g.requirements.minCredits > 0) DetailBullet("Min ${g.requirements.minCredits} credits")
                            if (g.requirements.minDays > 0) DetailBullet("Account at least ${g.requirements.minDays} days old")
                        }
                    }
                }

                if (g.participationReward.enabled && !isEnded) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.success.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🎁", fontSize = 20.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Participation bonus",
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.onBackground,
                                )
                                Text(
                                    "${if (g.participationReward.credits > 0) "💰 +${g.participationReward.credits} credits " else ""}${if (g.participationReward.coins > 0) "🪙 +${g.participationReward.coins} coins" else ""}",
                                    fontSize = 11.sp, color = colors.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (!isEnded && !isExpired) {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            joining = true
                            error = null
                            scope.launch {
                                val refs = userData.getReferralCount(uid)
                                val credits = userData.getCredits(uid)
                                val days = try {
                                    val userDoc = FirebaseFirestore.getInstance()
                                        .collection(FirebaseConfig.COL_USERS)
                                        .document(uid).get().await()
                                    val created = userDoc.getLong("createdAt") ?: 0L
                                    if (created > 0) ((now - created) / 86400L).toInt() else 0
                                } catch (_: Exception) { 0 }
                                val res = service.enterGiveaway(
                                    gwId = g.id,
                                    uid = uid,
                                    userName = "User",
                                    userRefCount = refs,
                                    userCredits = credits,
                                    userDaysOld = days,
                                    isForceJoinVerified = isForceJoinVerified,
                                )
                                joining = false
                                if (res.isSuccess) {
                                    success = "Joined!"
                                    refresh()
                                } else {
                                    error = res.exceptionOrNull()?.message ?: "Failed"
                                }
                            }
                        },
                        enabled = !entered && !joining,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (entered) colors.surface else colors.primary,
                            contentColor = if (entered) colors.success else Color.Black,
                        ),
                    ) {
                        if (joining) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.Black,
                            )
                        } else {
                            Text(
                                if (entered) "✓ Already Joined" else "Join Giveaway",
                                fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            )
                        }
                    }
                }

                if (isEnded && g.winners.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("🏆 Winners", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                    Spacer(Modifier.height(8.dp))
                    g.winners.forEach { w ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.surface,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    when (w.rank) {
                                        1 -> "🥇"
                                        2 -> "🥈"
                                        3 -> "🥉"
                                        else -> "#${w.rank}"
                                    },
                                    fontSize = 18.sp, modifier = Modifier.width(40.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        w.name,
                                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onBackground,
                                    )
                                    Text("🗳 ${w.votes}v · 👥 ${w.refs}r", fontSize = 10.sp, color = colors.onSurfaceVariant)
                                    if (w.prizesAwarded.isNotEmpty()) {
                                        Text(w.prizesAwarded.joinToString(" · "), fontSize = 11.sp, color = colors.primary)
                                    }
                                }
                            }
                        }
                    }
                }

                if (leaderboard.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("📊 Leaderboard", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(minOf(400.dp, (leaderboard.size * 55).dp)),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(leaderboard) { row ->
                            val isMe = row.uid == uid
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isMe) colors.primary.copy(alpha = 0.15f) else colors.surface,
                                modifier = Modifier.fillMaxWidth(),
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
                                        Text(
                                            row.name + if (isMe) " (you)" else "",
                                            fontSize = 12.sp,
                                            fontWeight = if (isMe) FontWeight.Bold else FontWeight.SemiBold,
                                            color = colors.onBackground,
                                        )
                                        Text(
                                            "🗳 ${row.votes}v · 👥 ${row.refs}r",
                                            fontSize = 10.sp, color = colors.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        "%.1f".format(row.score),
                                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                        color = colors.primary, fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
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
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
    val colors = LocalSanjiColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 11.sp, color = colors.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
    }
}

@Composable
private fun DetailBullet(text: String) {
    val colors = LocalSanjiColors.current
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("• ", fontSize = 11.sp, color = colors.primary)
        Text(text, fontSize = 11.sp, color = colors.onBackground)
    }
}