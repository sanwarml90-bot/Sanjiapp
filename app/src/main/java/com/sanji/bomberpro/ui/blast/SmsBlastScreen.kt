package com.sanji.bomberpro.ui.blast

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.FirebaseConfig
import com.sanji.bomberpro.data.AuthManager
import com.sanji.bomberpro.data.AuthState
import com.sanji.bomberpro.data.UserRole
import com.sanji.bomberpro.services.BlastEvent
import com.sanji.bomberpro.services.BlastExecutor
import com.sanji.bomberpro.services.BlastSpeed
import com.sanji.bomberpro.services.Device
import com.sanji.bomberpro.services.RealtimeDbService
import com.sanji.bomberpro.services.UserDataService
import com.sanji.bomberpro.services.UserDocObserver
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private enum class BlastStep { NUMBER, MESSAGE, SPEED_POWER, COUNT, PROGRESS, RESULT }

@Composable
fun SmsBlastScreen(
    authManager: AuthManager,
    onBack: () -> Unit,
    onOpenBulkMode: (() -> Unit)? = null,
) {
    val authState by authManager.authState.collectAsState()
    val logged = authState as? AuthState.LoggedIn

    if (logged == null) {
        onBack()
        return
    }

    val uid = logged.user.uid
    val userName = logged.profile?.displayName ?: (logged.user.displayName ?: "User")
    val userEmail = logged.profile?.email ?: (logged.user.email ?: "")
    val isAdmin = logged.role.priority >= UserRole.ADMIN.priority

    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val rtdb = remember { RealtimeDbService() }
    val userData = remember { UserDataService() }
    val executor = remember { BlastExecutor(rtdb) }

    var liveCredits by remember { mutableStateOf(logged.profile?.credits ?: 0) }
    var livePremium by remember { mutableStateOf(logged.profile?.premium ?: false) }

    LaunchedEffect(uid) {
        UserDocObserver.observeUserDoc(uid).collect { map ->
            liveCredits = (map["credits"] as? Number)?.toInt() ?: 0
            livePremium = map["premium"] as? Boolean ?: false
        }
    }

    var step by remember { mutableStateOf(BlastStep.NUMBER) }
    var number by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var speed by remember { mutableStateOf(BlastSpeed.FAST) }
    var powerMode by remember { mutableStateOf(FirebaseConfig.DEFAULT_POWER_MODE) }
    var countText by remember { mutableStateOf("") }

    var sent by remember { mutableStateOf(0) }
    var failed by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var rate by remember { mutableStateOf(0.0) }
    var duration by remember { mutableStateOf(0L) }
    var refunded by remember { mutableStateOf(0) }
    var finalBalance by remember { mutableStateOf(0) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var devices by remember { mutableStateOf<List<Device>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var reserved by remember { mutableStateOf(0) }
    var cancelFlag = remember { AtomicBoolean(false) }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val uri = data?.data
            if (uri != null) {
                try {
                    context.contentResolver.query(
                        uri,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        null, null, null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            if (idx >= 0) {
                                val raw = cursor.getString(idx) ?: ""
                                val cleaned = raw.trim()
                                    .replace(" ", "").replace("-", "")
                                    .replace("(", "").replace(")", "").replace(".", "")
                                if (cleaned.length >= 7) number = cleaned
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (step == BlastStep.PROGRESS) cancelFlag.set(true)
                else if (step == BlastStep.RESULT) {
                    step = BlastStep.NUMBER
                    number = ""
                    message = ""
                    countText = ""
                    errorMsg = null
                } else onBack()
            }) {
                Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
            }
            Spacer(Modifier.weight(1f))
            Surface(shape = RoundedCornerShape(20.dp), color = colors.primary.copy(alpha = 0.15f)) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "💰 $liveCredits",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            when (step) {
                BlastStep.NUMBER -> StepNumber(
                    number = number,
                    onChange = { number = it },
                    onOpenContacts = {
                        try {
                            contactPickerLauncher.launch(
                                Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                            )
                        } catch (_: Exception) {
                            errorMsg = "Could not open contacts"
                        }
                    },
                    isPremium = livePremium,
                    onOpenBulkMode = onOpenBulkMode,
                    onNext = {
                        val clean = number.replace(" ", "").replace("-", "").replace("+", "")
                        if (clean.length < 7 || !clean.all { it.isDigit() }) {
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
                        else { errorMsg = null; step = BlastStep.SPEED_POWER }
                    },
                    onBack = { step = BlastStep.NUMBER },
                )

                BlastStep.SPEED_POWER -> StepSpeedPower(
                    speed = speed,
                    onSpeedChange = { speed = it },
                    power = powerMode,
                    onPowerChange = { powerMode = it },
                    credits = liveCredits,
                    isAdmin = isAdmin,
                    onNext = { step = BlastStep.COUNT },
                    onBack = { step = BlastStep.MESSAGE },
                )

                BlastStep.COUNT -> StepCount(
                    countText = countText,
                    onChange = { countText = it.filter { c -> c.isDigit() } },
                    credits = liveCredits,
                    devicesCount = devices.size,
                    scanning = scanning,
                    power = powerMode,
                    isAdmin = isAdmin,
                    onScan = {
                        scanning = true
                        scope.launch {
                            try {
                                val firebases = rtdb.loadFirebases()
                                devices = rtdb.scanAll(firebases)
                            } catch (_: Exception) {
                                devices = emptyList()
                            } finally {
                                scanning = false
                            }
                        }
                    },
                    onStart = {
                        val cnt = countText.toIntOrNull() ?: 0
                        val effective = cnt * powerMode
                        when {
                            cnt <= 0 -> errorMsg = "Enter a valid count"
                            devices.isEmpty() -> errorMsg = "Scan devices first"
                            !isAdmin && effective > liveCredits ->
                                errorMsg = "Not enough credits (need $effective, have $liveCredits)"
                            else -> {
                                errorMsg = null
                                cancelFlag = AtomicBoolean(false)
                                sent = 0; failed = 0; total = 0; rate = 0.0
                                refunded = 0; finalBalance = 0; reserved = 0
                                step = BlastStep.PROGRESS

                                scope.launch {
                                    executor.execute(
                                        uid = uid,
                                        isAdmin = isAdmin,
                                        userData = userData,
                                        userName = userName,
                                        userEmail = userEmail,
                                        devices = devices,
                                        target = number,
                                        message = message,
                                        count = cnt,
                                        power = powerMode,
                                        threads = 20,
                                        speed = speed,
                                        cancelFlag = cancelFlag,
                                    ).collect { event ->
                                        when (event) {
                                            is BlastEvent.Started -> {
                                                total = event.total
                                                reserved = event.reservedCredits
                                            }
                                            is BlastEvent.Progress -> {
                                                sent = event.sent
                                                failed = event.failed
                                                rate = event.ratePerSec
                                            }
                                            is BlastEvent.Complete -> {
                                                duration = event.durationSec
                                                refunded = event.refundedCredits
                                                finalBalance = event.finalBalance
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
                        }
                    },
                    onBack = { step = BlastStep.SPEED_POWER },
                )

                BlastStep.PROGRESS -> StepProgress(
                    sent = sent,
                    failed = failed,
                    total = total,
                    rate = rate,
                    reserved = reserved,
                    power = powerMode,
                    isAdmin = isAdmin,
                    onStop = { cancelFlag.set(true) },
                )

                BlastStep.RESULT -> StepResult(
                    sent = sent,
                    failed = failed,
                    total = total,
                    duration = duration,
                    refunded = refunded,
                    finalBalance = finalBalance,
                    isAdmin = isAdmin,
                    errorMsg = errorMsg,
                    onDone = {
                        step = BlastStep.NUMBER
                        number = ""
                        message = ""
                        countText = ""
                        errorMsg = null
                    },
                )
            }

            if (errorMsg != null && step != BlastStep.RESULT) {
                Spacer(Modifier.height(12.dp))
                Text(errorMsg!!, color = colors.error, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun StepNumber(
    number: String,
    onChange: (String) -> Unit,
    onOpenContacts: () -> Unit,
    isPremium: Boolean,
    onOpenBulkMode: (() -> Unit)?,
    onNext: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    Text("Step 1 / 4 — Target Number", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
    Spacer(Modifier.height(8.dp))
    Text("Enter phone number with country code", fontSize = 12.sp, color = colors.onSurfaceVariant)
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = number,
        onValueChange = onChange,
        placeholder = { Text("+919876543210") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    Surface(
        onClick = onOpenContacts,
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Contacts, null, tint = colors.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Pick from contacts", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                Text("Choose a number from your phone contacts", fontSize = 11.sp, color = colors.onSurfaceVariant)
            }
        }
    }
    if (onOpenBulkMode != null) {
        Spacer(Modifier.height(10.dp))
        Surface(
            onClick = { if (isPremium) onOpenBulkMode() else Unit },
            shape = RoundedCornerShape(12.dp),
            color = if (isPremium) colors.primary.copy(alpha = 0.12f) else colors.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isPremium) Icons.Default.CheckCircle else Icons.Default.Lock,
                    null,
                    tint = if (isPremium) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Bulk Blast Mode",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = if (isPremium) colors.onBackground else colors.onSurfaceVariant,
                    )
                    Text(
                        if (isPremium) "Send to multiple numbers at once"
                        else "Premium feature — upgrade to unlock",
                        fontSize = 11.sp, color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    PrimaryButton("Next →", onNext, number.isNotBlank())
}

@Composable
private fun StepMessage(
    message: String,
    onChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
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
    PrimaryButton("Next →", onNext, message.isNotBlank())
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text("← Back", color = colors.onSurfaceVariant)
    }
}

@Composable
private fun StepSpeedPower(
    speed: BlastSpeed,
    onSpeedChange: (BlastSpeed) -> Unit,
    power: Int,
    onPowerChange: (Int) -> Unit,
    credits: Int,
    isAdmin: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    Text("Step 3 / 4 — Speed${if (isAdmin) " & Power" else ""}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
    Spacer(Modifier.height(8.dp))
    Text(
        if (isAdmin) "Choose speed and power multiplier" else "Choose send speed",
        fontSize = 12.sp, color = colors.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))

    Text("SPEED", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.primary, letterSpacing = 1.sp)
    Spacer(Modifier.height(6.dp))
    BlastSpeed.entries.forEach { s ->
        Surface(
            onClick = { onSpeedChange(s) },
            shape = RoundedCornerShape(12.dp),
            color = if (speed == s) colors.primary.copy(alpha = 0.15f) else colors.surface,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(s.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                    Text(
                        when (s) {
                            BlastSpeed.FAST -> "Maximum speed · 1ms delay"
                            BlastSpeed.MEDIUM -> "Balanced · 50ms delay"
                            BlastSpeed.SLOW -> "Safe · 200ms delay"
                        },
                        fontSize = 11.sp, color = colors.onSurfaceVariant,
                    )
                }
                if (speed == s) {
                    Icon(Icons.Default.CheckCircle, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }

    if (isAdmin) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("POWER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.primary, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Text("Count × $power", fontSize = 11.sp, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Higher power = more SMS from same count (admin only)",
            fontSize = 11.sp, color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        FirebaseConfig.ALLOWED_POWER_MODES.chunked(3).forEach { rowModes ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowModes.forEach { mode ->
                    Surface(
                        onClick = { onPowerChange(mode) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (power == mode) colors.primary.copy(alpha = 0.2f) else colors.surface,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "${mode}x",
                                fontSize = 18.sp, fontWeight = FontWeight.Black,
                                color = if (power == mode) colors.primary else colors.onBackground,
                            )
                            Text(
                                when (mode) {
                                    2 -> "Default"
                                    3 -> "Fast"
                                    4 -> "Aggressive"
                                    5 -> "Heavy"
                                    10 -> "Max"
                                    else -> ""
                                },
                                fontSize = 9.sp, color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
                repeat(3 - rowModes.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(6.dp))
        }
    }

    if (!isAdmin) {
        Spacer(Modifier.height(12.dp))
        Text("💰 Credits: $credits", fontSize = 12.sp, color = colors.onSurfaceVariant)
    }

    Spacer(Modifier.height(24.dp))
    PrimaryButton("Next →", onNext, true)
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text("← Back", color = colors.onSurfaceVariant)
    }
}

@Composable
private fun StepCount(
    countText: String,
    onChange: (String) -> Unit,
    credits: Int,
    devicesCount: Int,
    scanning: Boolean,
    power: Int,
    isAdmin: Boolean,
    onScan: () -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val baseCount = countText.toIntOrNull() ?: 0
    val effective = baseCount * power

    Text("Step 4 / 4 — Count", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
    Spacer(Modifier.height(8.dp))
    Text(
        if (isAdmin) "Base count per target (power multiplies it)" else "How many SMS to send",
        fontSize = 12.sp, color = colors.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))

    Surface(shape = RoundedCornerShape(12.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Online devices", fontSize = 12.sp, color = colors.onSurfaceVariant)
                Text("$devicesCount", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            }
            if (scanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.primary,
                )
            } else {
                TextButton(onClick = onScan) { Text("Scan", color = colors.primary) }
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    if (!isAdmin) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Credits: $credits", fontSize = 12.sp, color = colors.onSurfaceVariant)
            if (effective > 0) {
                Text(
                    "Cost: $effective",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = if (effective > credits) colors.error else colors.primary,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Failed sends are refunded automatically.", fontSize = 11.sp, color = colors.primary)
        Spacer(Modifier.height(12.dp))
    }

    OutlinedTextField(
        value = countText,
        onValueChange = onChange,
        placeholder = { Text("e.g. 500") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )

    if (isAdmin && effective > 0) {
        Spacer(Modifier.height(8.dp))
        Text(
            "$baseCount × $power = $effective total SMS",
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground,
        )
    }

    Spacer(Modifier.height(24.dp))
    PrimaryButton("START BLAST 🚀", onStart, countText.isNotBlank())
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text("← Back", color = colors.onSurfaceVariant)
    }
}

@Composable
private fun StepProgress(
    sent: Int,
    failed: Int,
    total: Int,
    rate: Double,
    reserved: Int,
    power: Int,
    isAdmin: Boolean,
    onStop: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val progress = if (total > 0) (sent + failed).toFloat() / total else 0f

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("BLASTING…", fontSize = 20.sp, fontWeight = FontWeight.Black, color = colors.primary)
        Spacer(Modifier.height(4.dp))
        Text(
            if (isAdmin) "Power ${power}x · Reserved $reserved" else "Reserved $reserved credits",
            fontSize = 11.sp, color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        LinearProgressIndicator(
            progress = { progress }, color = colors.primary, trackColor = colors.surface,
            modifier = Modifier.fillMaxWidth().height(10.dp),
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
            "⚡ ${"%.1f".format(rate)} / sec",
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.primary,
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
private fun StepResult(
    sent: Int,
    failed: Int,
    total: Int,
    duration: Long,
    refunded: Int,
    finalBalance: Int,
    isAdmin: Boolean,
    errorMsg: String?,
    onDone: () -> Unit,
) {
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
            Text(errorMsg, fontSize = 13.sp, color = colors.error, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatBlock("SENT", "$sent", colors.success)
            StatBlock("FAILED", "$failed", colors.error)
            StatBlock("TIME", "${duration}s", colors.primary)
        }

        if (!isAdmin) {
            Spacer(Modifier.height(20.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    if (refunded > 0) {
                        Text(
                            "♻️ Refunded $refunded credits (failed sends)",
                            fontSize = 13.sp, color = colors.success, fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        "💰 Final balance: $finalBalance credits",
                        fontSize = 13.sp, color = colors.onBackground, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        PrimaryButton("Done", onDone, true)
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
        modifier = Modifier.fillMaxWidth().height(56.dp),
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