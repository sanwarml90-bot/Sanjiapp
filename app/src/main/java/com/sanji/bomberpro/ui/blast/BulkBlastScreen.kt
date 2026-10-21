package com.sanji.bomberpro.ui.blast

import android.Manifest
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
import androidx.compose.material.icons.filled.Delete
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
import com.sanji.bomberpro.services.BlastSpeed
import com.sanji.bomberpro.services.BulkBlastConfig
import com.sanji.bomberpro.services.BulkBlastService
import com.sanji.bomberpro.services.BulkBlastSummary
import com.sanji.bomberpro.services.BulkCostEstimate
import com.sanji.bomberpro.services.ContactsService
import com.sanji.bomberpro.services.UserDocObserver
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private enum class BulkStep { PREMIUM_CHECK, NUMBERS, MESSAGE, CONFIG, EXECUTE, RESULT }

@Composable
fun BulkBlastScreen(
    authManager: AuthManager,
    onBack: () -> Unit,
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

    val bulkSvc = remember { BulkBlastService() }
    val contactsSvc = remember { ContactsService(context) }

    var livePremium by remember { mutableStateOf(logged.profile?.premium ?: false) }
    var liveCredits by remember { mutableStateOf(logged.profile?.credits ?: 0) }

    LaunchedEffect(uid) {
        UserDocObserver.observeUserDoc(uid).collect { map ->
            livePremium = map["premium"] as? Boolean ?: false
            liveCredits = (map["credits"] as? Number)?.toInt() ?: 0
        }
    }

    var step by remember { mutableStateOf(BulkStep.PREMIUM_CHECK) }
    var numbersList by remember { mutableStateOf<MutableList<String>>(mutableListOf()) }
    var manualNumberInput by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var perNumberCountText by remember { mutableStateOf("10") }
    var powerMode by remember { mutableStateOf(FirebaseConfig.DEFAULT_POWER_MODE) }
    var speed by remember { mutableStateOf(BlastSpeed.FAST) }
    var estimate by remember { mutableStateOf<BulkCostEstimate?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var progressIndex by remember { mutableStateOf(0) }
    var progressSent by remember { mutableStateOf(0) }
    var progressFailed by remember { mutableStateOf(0) }
    var progressTotalNumbers by remember { mutableStateOf(0) }
    var summary by remember { mutableStateOf<BulkBlastSummary?>(null) }
    var cancelFlag = remember { AtomicBoolean(false) }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                loading = true
                try {
                    val contacts = contactsSvc.loadContacts()
                    val all = mutableListOf<String>()
                    contacts.forEach { c -> all.addAll(c.phones) }
                    val cleaned = bulkSvc.cleanNumbers(all)
                    numbersList = cleaned.toMutableList()
                    error = null
                } catch (e: Exception) {
                    error = "Failed to load contacts: ${e.message}"
                } finally {
                    loading = false
                }
            }
        } else {
            error = "Contacts permission denied"
        }
    }

    // premium check on open
    LaunchedEffect(step) {
        if (step == BulkStep.PREMIUM_CHECK) {
            if (!isAdmin) {
                val isPrem = bulkSvc.isUserPremium(uid)
                livePremium = isPrem
            } else {
                livePremium = true
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (step == BulkStep.EXECUTE) {
                    cancelFlag.set(true)
                } else if (step == BulkStep.RESULT) {
                    // reset
                    step = BulkStep.NUMBERS
                    summary = null
                    error = null
                } else {
                    onBack()
                }
            }) {
                Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
            }
            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colors.primary.copy(alpha = 0.15f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "💰 $liveCredits",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            when (step) {
                BulkStep.PREMIUM_CHECK -> PremiumCheckStep(
                    isPremium = livePremium || isAdmin,
                    isAdmin = isAdmin,
                    onContinue = { step = BulkStep.NUMBERS },
                    onBack = onBack,
                )

                BulkStep.NUMBERS -> NumbersStep(
                    numbers = numbersList,
                    manualInput = manualNumberInput,
                    onManualInputChange = { manualNumberInput = it },
                    loading = loading,
                    onAddManual = {
                        val clean = bulkSvc.cleanNumbers(listOf(manualNumberInput))
                        if (clean.isEmpty()) {
                            error = "Invalid number"
                        } else if (clean.first() in numbersList) {
                            error = "Already in list"
                        } else {
                            numbersList = (numbersList + clean.first()).toMutableList()
                            manualNumberInput = ""
                            error = null
                        }
                    },
                    onRemove = { idx ->
                        val newList = numbersList.toMutableList()
                        if (idx in newList.indices) {
                            newList.removeAt(idx)
                            numbersList = newList
                        }
                    },
                    onClearAll = {
                        numbersList = mutableListOf()
                    },
                    onPickContacts = {
                        if (contactsSvc.hasPermission()) {
                            scope.launch {
                                loading = true
                                try {
                                    val contacts = contactsSvc.loadContacts()
                                    val all = mutableListOf<String>()
                                    contacts.forEach { c -> all.addAll(c.phones) }
                                    val cleaned = bulkSvc.cleanNumbers(all)
                                    val merged = (numbersList + cleaned).distinct().toMutableList()
                                    numbersList = merged
                                    error = null
                                } catch (e: Exception) {
                                    error = "Contacts failed: ${e.message}"
                                } finally {
                                    loading = false
                                }
                            }
                        } else {
                            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        }
                    },
                    onNext = {
                        if (numbersList.size < FirebaseConfig.BULK_MIN_NUMBERS) {
                            error = "Add at least ${FirebaseConfig.BULK_MIN_NUMBERS} numbers"
                        } else if (numbersList.size > FirebaseConfig.BULK_MAX_NUMBERS) {
                            error = "Max ${FirebaseConfig.BULK_MAX_NUMBERS} numbers per blast"
                        } else {
                            error = null
                            step = BulkStep.MESSAGE
                        }
                    },
                )

                BulkStep.MESSAGE -> MessageStep(
                    message = message,
                    onChange = { message = it },
                    onNext = {
                        if (message.isBlank()) error = "Message required"
                        else { error = null; step = BulkStep.CONFIG }
                    },
                    onBack = { step = BulkStep.NUMBERS },
                )

                BulkStep.CONFIG -> ConfigStep(
                    perNumberText = perNumberCountText,
                    onPerNumberChange = { perNumberCountText = it.filter { c -> c.isDigit() } },
                    power = powerMode,
                    onPowerChange = { powerMode = it },
                    speed = speed,
                    onSpeedChange = { speed = it },
                    numbersCount = numbersList.size,
                    credits = liveCredits,
                    isAdmin = isAdmin,
                    estimate = estimate,
                    onEstimate = {
                        val cnt = perNumberCountText.toIntOrNull() ?: 0
                        if (cnt <= 0) {
                            error = "Count must be > 0"
                            return@ConfigStep
                        }
                        scope.launch {
                            loading = true
                            estimate = bulkSvc.estimate(
                                numbersCount = numbersList.size,
                                perNumberCount = cnt,
                                power = powerMode,
                                uid = uid,
                                isAdmin = isAdmin,
                            )
                            loading = false
                        }
                    },
                    onStart = {
                        val cnt = perNumberCountText.toIntOrNull() ?: 0
                        if (cnt <= 0) {
                            error = "Count must be > 0"
                            return@ConfigStep
                        }
                        val est = estimate
                        if (est == null) {
                            error = "Tap Estimate first"
                            return@ConfigStep
                        }
                        if (!isAdmin && !est.affordable) {
                            error = "Not enough credits (need ${est.totalCredits})"
                            return@ConfigStep
                        }

                        error = null
                        cancelFlag = AtomicBoolean(false)
                        progressIndex = 0
                        progressSent = 0
                        progressFailed = 0
                        progressTotalNumbers = numbersList.size
                        step = BulkStep.EXECUTE

                        scope.launch {
                            val summaryResult = bulkSvc.executeBulk(
                                uid = uid,
                                isAdmin = isAdmin,
                                userName = userName,
                                userEmail = userEmail,
                                numbers = numbersList.toList(),
                                message = message,
                                config = BulkBlastConfig(
                                    perNumberCount = cnt,
                                    power = powerMode,
                                    speed = speed,
                                ),
                                onProgress = { idx, total, sent, failed ->
                                    progressIndex = idx + 1
                                    progressTotalNumbers = total
                                    progressSent = sent
                                    progressFailed = failed
                                },
                                cancelFlag = cancelFlag,
                            )
                            summary = summaryResult
                            step = BulkStep.RESULT
                        }
                    },
                    onBack = { step = BulkStep.MESSAGE },
                )

                BulkStep.EXECUTE -> ExecuteStep(
                    progressIndex = progressIndex,
                    totalNumbers = progressTotalNumbers,
                    sent = progressSent,
                    failed = progressFailed,
                    onStop = { cancelFlag.set(true) },
                )

                BulkStep.RESULT -> {
                    val s = summary
                    ResultStep(
                        summary = s,
                        isAdmin = isAdmin,
                        onDone = {
                            step = BulkStep.NUMBERS
                            summary = null
                            numbersList = mutableListOf()
                            message = ""
                            perNumberCountText = "10"
                            error = null
                            estimate = null
                        },
                    )
                }
            }

            if (error != null && step != BulkStep.RESULT) {
                Spacer(Modifier.height(12.dp))
                Text(error!!, color = colors.error, fontSize = 13.sp)
            }
        }
    }
}

// ============================================================
//  PREMIUM CHECK
// ============================================================
@Composable
private fun PremiumCheckStep(
    isPremium: Boolean,
    isAdmin: Boolean,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        if (isPremium) {
            Icon(
                Icons.Default.CheckCircle,
                null,
                tint = colors.success,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Bulk Blast Mode",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = colors.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (isAdmin) "Admin access — unlimited" else "Premium unlocked",
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Send SMS to multiple numbers at once:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onBackground,
                    )
                    Spacer(Modifier.height(8.dp))
                    BulletLine("📋 Paste or add numbers manually")
                    BulletLine("📞 Import from phone contacts")
                    BulletLine("💰 Each number costs credits")
                    BulletLine("🚀 Supports power modes 2x - 10x")
                    BulletLine("♻️ Failed sends refunded")
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = Color.Black,
                ),
            ) { Text("Continue", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        } else {
            Icon(
                Icons.Default.Lock,
                null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Premium Feature",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = colors.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Bulk Blast is available for premium users only.",
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "What you get with premium:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onBackground,
                    )
                    Spacer(Modifier.height(8.dp))
                    BulletLine("📞 Bulk blast from contacts")
                    BulletLine("🚀 All power modes 2x-10x")
                    BulletLine("💎 Unlimited OTP fetches")
                    BulletLine("🎁 Priority support")
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Contact admin to upgrade",
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onBack) {
            Text("← Back", color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun BulletLine(text: String) {
    val colors = LocalSanjiColors.current
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("• ", fontSize = 12.sp, color = colors.primary)
        Text(text, fontSize = 12.sp, color = colors.onBackground)
    }
}

// ============================================================
//  NUMBERS STEP
// ============================================================
@Composable
private fun NumbersStep(
    numbers: List<String>,
    manualInput: String,
    onManualInputChange: (String) -> Unit,
    loading: Boolean,
    onAddManual: () -> Unit,
    onRemove: (Int) -> Unit,
    onClearAll: () -> Unit,
    onPickContacts: () -> Unit,
    onNext: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Step 1 / 3 — Numbers",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Add numbers manually or import from contacts",
            fontSize = 12.sp,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = manualInput,
                onValueChange = onManualInputChange,
                placeholder = { Text("+919876543210") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
            Button(
                onClick = onAddManual,
                enabled = manualInput.isNotBlank(),
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = Color.Black,
                ),
            ) { Text("Add", fontWeight = FontWeight.Bold) }
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                onClick = onPickContacts,
                shape = RoundedCornerShape(12.dp),
                color = colors.surface,
                modifier = Modifier.weight(1f),
                enabled = !loading,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.primary,
                        )
                    } else {
                        Icon(Icons.Default.Contacts, null, tint = colors.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pick from contacts", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                    }
                }
            }
            if (numbers.isNotEmpty()) {
                Surface(
                    onClick = onClearAll,
                    shape = RoundedCornerShape(12.dp),
                    color = colors.error.copy(alpha = 0.15f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Delete, null, tint = colors.error, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear", fontSize = 12.sp, color = colors.error, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "📋 ${numbers.size} number${if (numbers.size == 1) "" else "s"} in list",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.primary,
        )

        Spacer(Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .height(200.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                if (numbers.isEmpty()) {
                    Text(
                        "No numbers yet",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                    )
                } else {
                    numbers.forEachIndexed { idx, num ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${idx + 1}. ",
                                fontSize = 11.sp,
                                color = colors.onSurfaceVariant,
                            )
                            Text(
                                num,
                                fontSize = 12.sp,
                                color = colors.onBackground,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { onRemove(idx) },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    "Remove",
                                    tint = colors.error,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onNext,
            enabled = numbers.size >= FirebaseConfig.BULK_MIN_NUMBERS,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = Color.Black,
            ),
        ) {
            Text("Next →", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

// ============================================================
//  MESSAGE STEP
// ============================================================
@Composable
private fun MessageStep(
    message: String,
    onChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Step 2 / 3 — Message", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        Spacer(Modifier.height(4.dp))
        Text("Message sent to all numbers", fontSize = 12.sp, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = message,
            onValueChange = { if (it.length <= 1000) onChange(it) },
            placeholder = { Text("Type your message…") },
            minLines = 5,
            maxLines = 10,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("${message.length} / 1000", fontSize = 11.sp, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onNext,
            enabled = message.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = Color.Black,
            ),
        ) { Text("Next →", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← Back", color = colors.onSurfaceVariant)
        }
    }
}

// ============================================================
//  CONFIG STEP
// ============================================================
@Composable
private fun ConfigStep(
    perNumberText: String,
    onPerNumberChange: (String) -> Unit,
    power: Int,
    onPowerChange: (Int) -> Unit,
    speed: BlastSpeed,
    onSpeedChange: (BlastSpeed) -> Unit,
    numbersCount: Int,
    credits: Int,
    isAdmin: Boolean,
    estimate: BulkCostEstimate?,
    onEstimate: () -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Step 3 / 3 — Config", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        Spacer(Modifier.height(4.dp))
        Text("$numbersCount numbers in list", fontSize = 12.sp, color = colors.onSurfaceVariant)

        Spacer(Modifier.height(16.dp))

        Text("Count per number", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = perNumberText,
            onValueChange = onPerNumberChange,
            placeholder = { Text("e.g. 10") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        Text("Speed", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BlastSpeed.entries.forEach { s ->
                Surface(
                    onClick = { onSpeedChange(s) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (speed == s) colors.primary.copy(alpha = 0.15f) else colors.surface,
                    modifier = Modifier.weight(1f),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            s.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (speed == s) colors.primary else colors.onBackground,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("Power mode", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FirebaseConfig.ALLOWED_POWER_MODES.forEach { mode ->
                Surface(
                    onClick = { onPowerChange(mode) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (power == mode) colors.primary.copy(alpha = 0.15f) else colors.surface,
                    modifier = Modifier.weight(1f),
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "${mode}x",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = if (power == mode) colors.primary else colors.onBackground,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                if (estimate != null) {
                    Text(
                        "📊 Cost estimate",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    EstRow("Numbers", "${estimate.numbersCount}")
                    EstRow("Per number", "${estimate.perNumberCount}")
                    EstRow("Power", "${estimate.power}x")
                    EstRow("Total SMS", "${estimate.totalSms}")
                    EstRow("Total credits", "${estimate.totalCredits}")
                    EstRow("Your credits", "$credits", highlight = !isAdmin && !estimate.affordable)
                    if (!isAdmin && !estimate.affordable) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "❌ Not affordable — max ${estimate.maxAffordable} numbers with current credits",
                            fontSize = 11.sp,
                            color = colors.error,
                        )
                    }
                } else {
                    Text(
                        "Tap 'Estimate Cost' to see total credits needed",
                        fontSize = 11.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        TextButton(
            onClick = onEstimate,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Estimate Cost", color = colors.primary, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onStart,
            enabled = estimate != null && (isAdmin || estimate.affordable),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = Color.Black,
            ),
        ) {
            Text("START BULK BLAST 🚀", fontWeight = FontWeight.Black, fontSize = 15.sp)
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← Back", color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun EstRow(label: String, value: String, highlight: Boolean = false) {
    val colors = LocalSanjiColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 11.sp, color = colors.onSurfaceVariant)
        Text(
            value,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) colors.error else colors.onBackground,
        )
    }
}

// ============================================================
//  EXECUTE STEP
// ============================================================
@Composable
private fun ExecuteStep(
    progressIndex: Int,
    totalNumbers: Int,
    sent: Int,
    failed: Int,
    onStop: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val progress = if (totalNumbers > 0) progressIndex.toFloat() / totalNumbers else 0f

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        Text("BULK BLASTING…", fontSize = 20.sp, fontWeight = FontWeight.Black, color = colors.primary)
        Spacer(Modifier.height(20.dp))

        LinearProgressIndicator(
            progress = { progress },
            color = colors.primary,
            trackColor = colors.surface,
            modifier = Modifier.fillMaxWidth().height(10.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Number $progressIndex of $totalNumbers",
            fontSize = 12.sp,
            color = colors.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$sent", fontSize = 24.sp, fontWeight = FontWeight.Black, color = colors.success)
                Text("SENT", fontSize = 11.sp, color = colors.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$failed", fontSize = 24.sp, fontWeight = FontWeight.Black, color = colors.error)
                Text("FAILED", fontSize = 11.sp, color = colors.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = Color.White),
        ) { Text("STOP", fontWeight = FontWeight.Black) }
    }
}

// ============================================================
//  RESULT STEP
// ============================================================
@Composable
private fun ResultStep(
    summary: BulkBlastSummary?,
    isAdmin: Boolean,
    onDone: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val success = summary?.error == null && (summary?.totalFailed ?: 0) == 0

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        Icon(
            imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = if (success) colors.success else colors.error,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (success) "Bulk Blast Complete" else "Bulk Blast Done",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onBackground,
        )

        if (summary?.error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                summary.error,
                fontSize = 12.sp,
                color = colors.error,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(20.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                EstRow("Numbers attempted", "${summary?.numbersAttempted ?: 0}")
                EstRow("Total sent", "${summary?.totalSent ?: 0}")
                EstRow("Total failed", "${summary?.totalFailed ?: 0}")
                EstRow("Duration", "${summary?.durationSec ?: 0}s")
                if (summary?.cancelled == true) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "⚠️ Cancelled early",
                        fontSize = 11.sp,
                        color = colors.warning,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = Color.Black,
            ),
        ) { Text("Done", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
    }
}