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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.data.ForceJoinChannel
import com.sanji.bomberpro.data.GiveawayRequirements
import com.sanji.bomberpro.data.ParticipationReward
import com.sanji.bomberpro.data.PrizeItem
import com.sanji.bomberpro.services.ForceJoinService
import com.sanji.bomberpro.services.GiveawayService
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch

@Composable
fun GiveawayCreateScreen(
    adminUid: String,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val service = remember { GiveawayService() }
    val forceJoin = remember { ForceJoinService() }

    var title by remember { mutableStateOf("") }
    var prize by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var winnersCountText by remember { mutableStateOf("1") }
    var winnerMethod by remember { mutableStateOf("votes_and_refs") }
    var durationHoursText by remember { mutableStateOf("24") }

    var reqForceJoin by remember { mutableStateOf(true) }
    var reqMinRefsText by remember { mutableStateOf("0") }
    var reqMinCreditsText by remember { mutableStateOf("0") }
    var reqMinDaysText by remember { mutableStateOf("0") }

    var rewardEnabled by remember { mutableStateOf(false) }
    var rewardCreditsText by remember { mutableStateOf("0") }
    var rewardCoinsText by remember { mutableStateOf("0") }

    var prizeCreditsText by remember { mutableStateOf("0") }
    var prizeCoinsText by remember { mutableStateOf("0") }

    var channels by remember { mutableStateOf<List<ForceJoinChannel>>(emptyList()) }
    var selectedChannelId by remember { mutableStateOf("") }
    var selectedChannelLink by remember { mutableStateOf("") }

    var loadingChannels by remember { mutableStateOf(true) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loadingChannels = true
        try {
            val state = forceJoin.loadForceJoinState()
            channels = state.channels
            if (state.channels.isNotEmpty()) {
                selectedChannelId = state.channels.first().id
                selectedChannelLink = state.channels.first().link
            }
        } finally {
            loadingChannels = false
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
                "New Giveaway",
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = colors.onBackground, modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        // basics
        SectionLabel("BASICS")
        FieldLabel("Title")
        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(80) },
            placeholder = { Text("e.g. iPhone 15 Pro Giveaway") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))
        FieldLabel("Prize (short)")
        OutlinedTextField(
            value = prize,
            onValueChange = { prize = it.take(200) },
            placeholder = { Text("e.g. iPhone 15 Pro 256GB") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))
        FieldLabel("Description (optional)")
        OutlinedTextField(
            value = description,
            onValueChange = { description = it.take(800) },
            placeholder = { Text("Terms, details, extra info…") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))
        SectionLabel("CONFIG")
        FieldLabel("Winners")
        OutlinedTextField(
            value = winnersCountText,
            onValueChange = { winnersCountText = it.filter { c -> c.isDigit() } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))
        FieldLabel("Winner method")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "votes_and_refs" to "Votes + refs",
                "votes_only" to "Votes",
                "refs_only" to "Refs",
            ).forEach { (key, label) ->
                Surface(
                    onClick = { winnerMethod = key },
                    shape = RoundedCornerShape(10.dp),
                    color = if (winnerMethod == key) colors.primary.copy(alpha = 0.15f) else colors.surface,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (winnerMethod == key) colors.primary else colors.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        FieldLabel("Duration (hours)")
        OutlinedTextField(
            value = durationHoursText,
            onValueChange = { durationHoursText = it.filter { c -> c.isDigit() } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))
        SectionLabel("CHANNEL")
        if (loadingChannels) {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
        } else if (channels.isEmpty()) {
            Surface(shape = RoundedCornerShape(12.dp), color = colors.error.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("No channels added", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.error)
                    Text(
                        "Add a force-join channel first (Admin → Force Join).",
                        fontSize = 11.sp, color = colors.onSurfaceVariant,
                    )
                }
            }
        } else {
            channels.forEach { ch ->
                Surface(
                    onClick = {
                        selectedChannelId = ch.id
                        selectedChannelLink = ch.link
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedChannelId == ch.id) colors.primary.copy(alpha = 0.15f) else colors.surface,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (selectedChannelId == ch.id) "✅" else "📢",
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                ch.title.ifBlank { "Channel" },
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground,
                            )
                            Text(ch.link, fontSize = 10.sp, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("REQUIREMENTS")
        ToggleField(
            label = "Force join required",
            desc = "Users must join channels first",
            checked = reqForceJoin,
            onChange = { reqForceJoin = it },
        )
        Spacer(Modifier.height(8.dp))
        FieldLabel("Min referrals (0 = none)")
        OutlinedTextField(
            value = reqMinRefsText,
            onValueChange = { reqMinRefsText = it.filter { c -> c.isDigit() } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        FieldLabel("Min credits (0 = none)")
        OutlinedTextField(
            value = reqMinCreditsText,
            onValueChange = { reqMinCreditsText = it.filter { c -> c.isDigit() } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        FieldLabel("Min account age days (0 = none)")
        OutlinedTextField(
            value = reqMinDaysText,
            onValueChange = { reqMinDaysText = it.filter { c -> c.isDigit() } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))
        SectionLabel("PARTICIPATION REWARD")
        ToggleField(
            label = "Give reward to all participants",
            desc = "Credits/coins distributed on join",
            checked = rewardEnabled,
            onChange = { rewardEnabled = it },
        )
        if (rewardEnabled) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    FieldLabel("Credits")
                    OutlinedTextField(
                        value = rewardCreditsText,
                        onValueChange = { rewardCreditsText = it.filter { c -> c.isDigit() } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    FieldLabel("Coins")
                    OutlinedTextField(
                        value = rewardCoinsText,
                        onValueChange = { rewardCoinsText = it.filter { c -> c.isDigit() } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("PRIZE FOR WINNERS")
        Text(
            "Awarded to rank 1 (same prize for all ranks by default)",
            fontSize = 10.sp, color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                FieldLabel("Credits")
                OutlinedTextField(
                    value = prizeCreditsText,
                    onValueChange = { prizeCreditsText = it.filter { c -> c.isDigit() } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                FieldLabel("Coins")
                OutlinedTextField(
                    value = prizeCoinsText,
                    onValueChange = { prizeCoinsText = it.filter { c -> c.isDigit() } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                val winners = winnersCountText.toIntOrNull() ?: 0
                val hours = durationHoursText.toIntOrNull() ?: 0
                if (title.isBlank()) { error = "Title required"; return@Button }
                if (prize.isBlank()) { error = "Prize required"; return@Button }
                if (winners <= 0) { error = "Winners must be > 0"; return@Button }
                if (hours <= 0) { error = "Duration must be > 0 hours"; return@Button }
                if (selectedChannelId.isBlank()) { error = "Select a channel"; return@Button }

                val endsAt = System.currentTimeMillis() / 1000L + hours * 3600L
                val pCredits = prizeCreditsText.toIntOrNull() ?: 0
                val pCoins = prizeCoinsText.toIntOrNull() ?: 0
                val prizeConfig = mutableMapOf<String, List<PrizeItem>>()
                for (rank in 1..winners) {
                    val items = mutableListOf<PrizeItem>()
                    if (pCredits > 0) items.add(PrizeItem("credits", pCredits))
                    if (pCoins > 0) items.add(PrizeItem("coins", pCoins))
                    if (items.isEmpty()) items.add(PrizeItem("custom", 0, "Rank $rank prize"))
                    prizeConfig[rank.toString()] = items
                }

                submitting = true
                error = null
                scope.launch {
                    val res = service.createGiveaway(
                        createdBy = adminUid,
                        title = title.trim(),
                        prize = prize.trim(),
                        description = description.trim(),
                        channelId = selectedChannelId,
                        channelLink = selectedChannelLink,
                        endsAt = endsAt,
                        winnersCount = winners,
                        winnerMethod = winnerMethod,
                        requirements = GiveawayRequirements(
                            forceJoin = reqForceJoin,
                            minRefs = reqMinRefsText.toIntOrNull() ?: 0,
                            minCredits = reqMinCreditsText.toIntOrNull() ?: 0,
                            minDays = reqMinDaysText.toIntOrNull() ?: 0,
                        ),
                        participationReward = ParticipationReward(
                            enabled = rewardEnabled,
                            credits = rewardCreditsText.toIntOrNull() ?: 0,
                            coins = rewardCoinsText.toIntOrNull() ?: 0,
                            distributed = false,
                        ),
                        prizeConfig = prizeConfig,
                    )
                    submitting = false
                    if (res.isSuccess) {
                        success = "Created!"
                        onCreated(res.getOrNull() ?: "")
                    } else {
                        error = res.exceptionOrNull()?.message ?: "Failed"
                    }
                }
            },
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = Color.Black,
            ),
        ) {
            if (submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.Black,
                )
            } else {
                Text("Create Giveaway", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        Spacer(Modifier.height(24.dp))
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
private fun SectionLabel(text: String) {
    val colors = LocalSanjiColors.current
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = colors.primary,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun FieldLabel(text: String) {
    val colors = LocalSanjiColors.current
    Text(
        text,
        fontSize = 11.sp,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun ToggleField(
    label: String,
    desc: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colors = LocalSanjiColors.current
    Surface(shape = RoundedCornerShape(10.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                Text(desc, fontSize = 10.sp, color = colors.onSurfaceVariant)
            }
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.primary,
                    checkedTrackColor = colors.primary.copy(alpha = 0.4f),
                ),
            )
        }
    }
}