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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.data.UserProfile
import com.sanji.bomberpro.services.BlastEvent
import com.sanji.bomberpro.services.BlastExecutor
import com.sanji.bomberpro.services.BlastSpeed
import com.sanji.bomberpro.services.Device
import com.sanji.bomberpro.services.RealtimeDbService
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private enum class BlastStep { NUMBER, MESSAGE, SPEED, COUNT, PROGRESS, RESULT }

@Composable
fun SmsBlastScreen(
    profile: UserProfile?,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()

    val rtdb = remember { RealtimeDbService() }
    val executor = remember { BlastExecutor(rtdb) }

    var step by remember { mutableStateOf(BlastStep.NUMBER) }
    var number by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var speed by remember { mutableStateOf(BlastSpeed.FAST) }
    var countText by remember { mutableStateOf("") }

    var sent by remember { mutableStateOf(0) }
    var failed by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var rate by remember { mutableStateOf(0.0) }
    var duration by remember { mutableStateOf(0L) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var devices by remember { mutableStateOf<List<Device>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var cancelFlag = remember { AtomicBoolean(false) }

    val credits = profile?.credits ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp),
    ) {
        // Top bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (step == BlastStep.PROGRESS) cancelFlag.set(true) else onBack()
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.onBackground)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "SMS BLAST · ${step.name}",
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(16.dp))

        when (step) {
            BlastStep.NUMBER -> StepNumber(
                number = number,
                onChange = { number = it },
                onNext = {
                    val clean = number.replace(" ", "").replace("-", "").replace("+", "")
                    if (clean.length < 7) {
                        errorMsg = "Invalid number (min 7 digits)"
                    } else {
                        errorMsg = null
                        step = BlastStep.MESSAGE
                    }
                },
            )

            BlastStep.MESSAGE -> StepMessage(
                message = message,
                onChange = { message = it },
                onNext = {
                    if (message.isBlank()) errorMsg = "Message required"
                    else { errorMsg = null; step = BlastStep.SPEED }
                },
                onBack = { step = BlastStep.NUMBER },
            )

            BlastStep.SPEED -> StepSpeed(
                selected = speed,
                onSelect = { speed = it },
                onNext = { step = BlastStep.COUNT },
            )

            BlastStep.COUNT -> StepCount(
                countText = countText,
                onChange = { countText = it },
                credits = credits,
                devicesCount = devices.size,
                scanning = scanning,
                onScan = {
                    scanning = true
                    scope.launch {
                        val firebases = rtdb.loadFirebases()
                        devices = rtdb.scanAll(firebases)
                        scanning = false
                    }
                },
                onStart = {
                    val cnt = countText.toIntOrNull() ?: 0
                    if (cnt <= 0) { errorMsg = "Enter a valid count" }
                    else if (devices.isEmpty()) { errorMsg = "Scan devices first" }
                    else if (cnt > credits && credits > 0) {
                        errorMsg = "Not enough credits (you have $credits)"
                    } else {
                        errorMsg = null
                        cancelFlag = AtomicBoolean(false)
                        sent = 0; failed = 0; total = cnt; rate = 0.0
                        step = BlastStep.PROGRESS

                        scope.launch {
                            executor.execute(
                                devices = devices,
                                target = number,
                                message = message,
                                count = cnt,
                                threads = 20,
                                speed = speed,
                                cancelFlag = cancelFlag,
                            ).collect { event ->
                                when (event) {
                                    is BlastEvent.Started -> { total = event.total }
                                    is BlastEvent.Progress -> {
                                        sent = event.sent
                                        failed = event.failed
                                        rate = event.ratePerSec
                                    }
                                    is BlastEvent.Complete -> {
                                        duration = event.durationSec
                                        step = BlastStep.RESULT
                                    }
                                    is BlastEvent.Error -> {
                                        errorMsg = event.message
                                        step = BlastStep.RESULT
                                    }
                                }
                            }
                        }
                    }
                },
                onBack = { step = BlastStep.SPEED },
            )

            BlastStep.PROGRESS -> StepProgress(
                sent = sent,
                failed = failed,
                total = total,
                rate = rate,
                onStop = { cancelFlag.set(true) },
            )

            BlastStep.RESULT -> StepResult(
                sent = sent,
                failed = failed,
                total = total,
                duration = duration,
                errorMsg = errorMsg,
                onDone = onBack,
            )
        }

        if (errorMsg != null && step != BlastStep.RESULT) {
            Spacer(Modifier.height(12.dp))
            Text(errorMsg!!, color = colors.error, fontSize = 13.sp)
        }
    }
}

@Composable
private fun StepNumber(number: String, onChange: (String) -> Unit, onNext: () -> Unit) {
    val colors = LocalSanjiColors.current
    Text("Step 1 / 4 — Target Number", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
    Spacer(Modifier.height(8.dp))
    Text("Enter phone number (with country code)", fontSize = 12.sp, color = colors.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))

    OutlinedTextField(
        value = number,
        onValueChange = onChange,
        placeholder = { Text("+919876543210") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(24.dp))
    PrimaryButton(label = "Next →", onClick = onNext, enabled = number.isNotBlank())
}

@Composable
private fun StepMessage(message: String, onChange: (String) -> Unit, onNext: () -> Unit, onBack: () -> Unit) {
    val colors = LocalSanjiColors.current
    Text("Step 2 / 4 — Message", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
    Spacer(Modifier.height(8.dp))
    Text("Custom message (max 1000 chars)", fontSize = 12.sp, color = colors.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))

    OutlinedTextField(
        value = message,
        onValueChange = { if (it.length <= 1000) onChange(it) },
        placeholder = { Text("Enter your message…") },
        minLines = 4,
        maxLines = 8,
        modifier = Modifier.fillMaxWidth(),
    )
    Text("${message.length} / 1000", fontSize = 11.sp, color = colors.onSurfaceVariant)

    Spacer(Modifier.height(24.dp))
    PrimaryButton(label = "Next →", onClick = onNext, enabled = message.isNotBlank())
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text("← Back", color = colors.onSurfaceVariant)
    }
}

@Composable
private fun StepSpeed(selected: BlastSpeed, onSelect: (BlastSpeed) -> Unit, onNext: () -> Unit) {
    val colors = LocalSanjiColors.current
    Text("Step 3 / 4 — Speed", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
    Spacer(Modifier.height(8.dp))
    Text("How fast should messages go?", fontSize = 12.sp, color = colors.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))

    BlastSpeed.entries.forEach { s ->
        Surface(
            onClick = { onSelect(s) },
            shape = RoundedCornerShape(14.dp),
            color = if (selected == s) colors.primary.copy(alpha = 0.15f) else colors.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(s.label, fontWeight = FontWeight.Bold, color = colors.onBackground)
                    Text(
                        text = when (s) {
                            BlastSpeed.FAST -> "Maximum speed · 1ms delay"
                            BlastSpeed.MEDIUM -> "Balanced · 50ms delay"
                            BlastSpeed.SLOW -> "Safe · 200ms delay"
                        },
                        fontSize = 11.sp, color = colors.onSurfaceVariant
                    )
                }
                if (selected == s) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.primary)
                }
            }
        }
    }

    Spacer(Modifier.height(24.dp))
    PrimaryButton(label = "Next →", onClick = onNext, enabled = true)
}

@Composable
private fun StepCount(
    countText: String,
    onChange: (String) -> Unit,
    credits: Int,
    devicesCount: Int,
    scanning: Boolean,
    onScan: () -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    Text("Step 4 / 4 — Count", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
    Spacer(Modifier.height(8.dp))

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Online devices", fontSize = 12.sp, color = colors.onSurfaceVariant)
                Text("$devicesCount", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            }
            if (scanning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.primary)
            } else {
                TextButton(onClick = onScan) { Text("Scan", color = colors.primary) }
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    Text("Credits available: ${credits}", fontSize = 12.sp, color = colors.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = countText,
        onValueChange = onChange,
        placeholder = { Text("e.g. 500") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(24.dp))
    PrimaryButton(label = "START BLAST 🚀", onClick = onStart, enabled = countText.isNotBlank())
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text("← Back", color = colors.onSurfaceVariant)
    }
}

@Composable
private fun StepProgress(sent: Int, failed: Int, total: Int, rate: Double, onStop: () -> Unit) {
    val colors = LocalSanjiColors.current
    val progress = if (total > 0) (sent + failed).toFloat() / total else 0f

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("BLASTING…", fontSize = 20.sp, fontWeight = FontWeight.Black, color = colors.primary)
        Spacer(Modifier.height(24.dp))

        LinearProgressIndicator(
            progress = { progress },
            color = colors.primary,
            trackColor = colors.surface,
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text("${(progress * 100).toInt()}%", fontSize = 12.sp, color = colors.onSurfaceVariant)

        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatBlock("SENT", "$sent", colors.success)
            StatBlock("FAILED", "$failed", colors.error)
            StatBlock("TOTAL", "$total", colors.primary)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "⚡ ${"%.1f".format(rate)} / sec",
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.primary
        )

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = Color.White),
        ) { Text("STOP", fontWeight = FontWeight.Black) }
    }
}

@Composable
private fun StepResult(sent: Int, failed: Int, total: Int, duration: Long, errorMsg: String?, onDone: () -> Unit) {
    val colors = LocalSanjiColors.current
    val success = errorMsg == null && failed == 0

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = if (success) colors.success else colors.error,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (errorMsg != null) "Blast Error" else if (success) "Blast Complete" else "Blast Done",
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.onBackground,
        )
        if (errorMsg != null) {
            Spacer(Modifier.height(8.dp))
            Text(errorMsg, fontSize = 13.sp, color = colors.error)
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatBlock("SENT", "$sent", colors.success)
            StatBlock("FAILED", "$failed", colors.error)
            StatBlock("TIME", "${duration}s", colors.primary)
        }
        Spacer(Modifier.height(32.dp))
        PrimaryButton(label = "Done", onClick = onDone, enabled = true)
    }
}

@Composable
private fun StatBlock(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black, color = color)
        Text(label, fontSize = 11.sp, color = LocalSanjiColors.current.onSurfaceVariant)
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit, enabled: Boolean) {
    val colors = LocalSanjiColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = Color.Black,
            disabledContainerColor = colors.surface,
            disabledContentColor = colors.onSurfaceVariant,
        ),
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}