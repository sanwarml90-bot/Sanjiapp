package com.sanji.bomberpro.ui.blast

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.sanji.bomberpro.FirebaseConfig
import com.sanji.bomberpro.services.BlastSpeed
import com.sanji.bomberpro.services.ScheduledBlast
import com.sanji.bomberpro.services.SchedulerService
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val FMT = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
private fun fmt(ts: Long): String =
    try { FMT.format(Date(ts * 1000L)) } catch (_: Exception) { "—" }

private enum class SchedView { LIST, CREATE }

@Composable
fun ScheduleBlastScreen(
    uid: String,
    userName: String,
    userEmail: String,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val service = remember { SchedulerService() }

    var view by remember { mutableStateOf(SchedView.LIST) }
    var schedules by remember { mutableStateOf<List<ScheduledBlast>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    var target by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var countText by remember { mutableStateOf("10") }
    var power by remember { mutableStateOf(FirebaseConfig.DEFAULT_POWER_MODE) }
    var speed by remember { mutableStateOf(BlastSpeed.FAST) }
    var repeatMinutesText by remember { mutableStateOf("0") }
    var delayMinutesText by remember { mutableStateOf("30") }
    var submitting by remember { mutableStateOf(false) }

    suspend fun refresh() {
        loading = true
        try {
            schedules = service.listUserSchedules(uid)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    LaunchedEffect(error) {
        if (error != null) {
            kotlinx.coroutines.delay(4000)
            error = null
        }
    }
    LaunchedEffect(success) {
        if (success != null) {
            kotlinx.coroutines.delay(4000)
            success = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        if (view == SchedView.LIST) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
                    }
                    Text(
                        "Scheduled Blasts",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        color = colors.onBackground, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { scope.launch { refresh() } }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = colors.primary)
                    }
                    IconButton(onClick = { view = SchedView.CREATE }) {
                        Icon(Icons.Default.Add, "New", tint = colors.primary)
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.primary)
                    }
                } else if (schedules.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⏰", fontSize = 48.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("No scheduled blasts", fontSize = 14.sp, color = colors.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { view = SchedView.CREATE },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary, contentColor = Color.Black,
                                ),
                            ) { Text("Schedule a Blast") }
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(schedules) { s ->
                            Surface(shape = RoundedCornerShape(14.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "⏰ ${fmt(s.scheduledAt)}",
                                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                            color = colors.onBackground, modifier = Modifier.weight(1f),
                                        )
                                        IconButton(onClick = {
                                            scope.launch {
                                                if (service.cancelSchedule(s.id)) {
                                                    success = "Cancelled"
                                                    refresh()
                                                } else {
                                                    error = "Cancel failed"
                                                }
                                            }
                                        }) {
                                            Icon(Icons.Default.Delete, "Cancel", tint = colors.error)
                                        }
                                    }
                                    Text(
                                        "📞 ${s.target}",
                                        fontSize = 12.sp, color = colors.onBackground,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    Text(
                                        "💬 ${s.message.take(60)}${if (s.message.length > 60) "…" else ""}",
                                        fontSize = 11.sp, color = colors.onSurfaceVariant,
                                    )
                                    Text(
                                        "× ${s.count} · ${s.power}x · ${s.speed}" +
                                                if (s.repeatMinutes > 0) " · repeats every ${s.repeatMinutes}m" else "",
                                        fontSize = 11.sp, color = colors.primary,
                                    )
                                    Text("Status: ${s.status}", fontSize = 10.sp, color = colors.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize()
                    .background(colors.background)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { view = SchedView.LIST }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
                    }
                    Text(
                        "Schedule Blast",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        color = colors.onBackground, modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text("Target number", fontSize = 12.sp, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    placeholder = { Text("+919876543210") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                Text("Message", fontSize = 12.sp, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(1000) },
                    placeholder = { Text("Enter your message…") },
                    minLines = 4,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${message.length} / 1000", fontSize = 10.sp, color = colors.onSurfaceVariant)

                Spacer(Modifier.height(12.dp))
                Text("Base count", fontSize = 12.sp, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = countText,
                    onValueChange = { countText = it.filter { c -> c.isDigit() } },
                    placeholder = { Text("e.g. 100") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                Text("Power mode", fontSize = 12.sp, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FirebaseConfig.ALLOWED_POWER_MODES.forEach { p ->
                        Surface(
                            onClick = { power = p },
                            shape = RoundedCornerShape(10.dp),
                            color = if (power == p) colors.primary.copy(alpha = 0.15f) else colors.surface,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                "${p}x",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (power == p) colors.primary else colors.onBackground,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Speed", fontSize = 12.sp, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BlastSpeed.entries.forEach { s ->
                        Surface(
                            onClick = { speed = s },
                            shape = RoundedCornerShape(10.dp),
                            color = if (speed == s) colors.primary.copy(alpha = 0.15f) else colors.surface,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                s.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (speed == s) colors.primary else colors.onBackground,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Delay (minutes from now)", fontSize = 12.sp, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = delayMinutesText,
                    onValueChange = { delayMinutesText = it.filter { c -> c.isDigit() } },
                    placeholder = { Text("e.g. 30") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                Text("Repeat every N minutes (0 = one-shot)", fontSize = 12.sp, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = repeatMinutesText,
                    onValueChange = { repeatMinutesText = it.filter { c -> c.isDigit() } },
                    placeholder = { Text("0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        val cnt = countText.toIntOrNull() ?: 0
                        val delayMin = delayMinutesText.toIntOrNull() ?: 0
                        val rep = repeatMinutesText.toIntOrNull() ?: 0
                        if (target.isBlank()) { error = "Target required"; return@Button }
                        if (message.isBlank()) { error = "Message required"; return@Button }
                        if (cnt <= 0) { error = "Count must be > 0"; return@Button }
                        if (delayMin <= 0) { error = "Delay must be >= 1 min"; return@Button }

                        val cal = Calendar.getInstance()
                        cal.add(Calendar.MINUTE, delayMin)
                        val scheduledAt = cal.timeInMillis / 1000L

                        submitting = true
                        scope.launch {
                            val res = service.scheduleBlast(
                                uid = uid,
                                userName = userName,
                                userEmail = userEmail,
                                target = target.trim(),
                                message = message.trim(),
                                count = cnt,
                                power = power,
                                speed = speed.label,
                                scheduledAt = scheduledAt,
                                repeatMinutes = rep,
                            )
                            submitting = false
                            if (res.isSuccess) {
                                success = "Scheduled for ${fmt(scheduledAt)}"
                                target = ""
                                message = ""
                                countText = "10"
                                delayMinutesText = "30"
                                repeatMinutesText = "0"
                                view = SchedView.LIST
                                refresh()
                            } else {
                                error = res.exceptionOrNull()?.message ?: "Failed"
                            }
                        }
                    },
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary, contentColor = Color.Black,
                    ),
                ) {
                    if (submitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.Black)
                    } else {
                        Text("Schedule Blast", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { view = SchedView.LIST }, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel", color = colors.onSurfaceVariant)
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