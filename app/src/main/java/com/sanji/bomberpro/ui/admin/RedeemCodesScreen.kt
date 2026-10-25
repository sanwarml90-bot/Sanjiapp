package com.sanji.bomberpro.ui.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlusOne
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.services.RedeemCode
import com.sanji.bomberpro.services.SettingsService
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FMT_SHORT = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
private fun fmtShort(ts: Long): String =
    try { FMT_SHORT.format(Date(ts * 1000L)) } catch (_: Exception) { "—" }

@Composable
fun RedeemCodesScreen(
    adminUid: String,
    service: SettingsService,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var codes by remember { mutableStateOf<List<RedeemCode>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showCreate by remember { mutableStateOf(false) }
    var lastCreatedCode by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        loading = true
        codes = service.listRedeemCodes()
        loading = false
    }

    fun copyCode(code: String) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Redeem code", code))
            success = "Copied: $code"
        } catch (_: Exception) {
            error = "Copy failed"
        }
    }

    LaunchedEffect(Unit) { refresh() }

    LaunchedEffect(error) {
        if (error != null) { kotlinx.coroutines.delay(3000); error = null }
    }
    LaunchedEffect(success) {
        if (success != null) { kotlinx.coroutines.delay(2000); success = null }
    }

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
                "Redeem Codes (${codes.size})",
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = colors.onBackground, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { scope.launch { refresh() } }) {
                Icon(Icons.Default.Refresh, "Refresh", tint = colors.primary)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Prominent create button — full-width, always visible
        Button(
            onClick = { showCreate = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = Color.Black,
            ),
        ) {
            Icon(Icons.Default.PlusOne, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Create New Redeem Code", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Spacer(Modifier.height(16.dp))

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
        } else if (codes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎟", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("No codes yet", fontSize = 14.sp, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap Create New Redeem Code above to get started",
                        fontSize = 11.sp, color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            Text(
                "Tap any code to copy · tap 🗑 to delete",
                fontSize = 11.sp, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(codes) { c ->
                    Surface(
                        onClick = { copyCode(c.code) },
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    c.code,
                                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                    color = colors.primary, fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    when (c.type) {
                                        "subscription" -> "📦 ${c.plan.uppercase()} · ${c.days} days"
                                        "coins" -> "🪙 ${c.amount} coins"
                                        else -> "💰 ${c.amount} credits"
                                    },
                                    fontSize = 11.sp, color = colors.onBackground,
                                )
                                Text(
                                    "Uses left: ${c.usesLeft} · ${fmtShort(c.createdAt)}",
                                    fontSize = 10.sp, color = colors.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { copyCode(c.code) }) {
                                Icon(
                                    Icons.Default.ContentCopy, "Copy code",
                                    tint = colors.primary, modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    service.deleteRedeemCode(c.code)
                                    refresh()
                                }
                            }) {
                                Icon(Icons.Default.Delete, "Delete", tint = colors.error)
                            }
                        }
                    }
                }
            }
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = colors.error, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        if (success != null) {
            Spacer(Modifier.height(12.dp))
            Text(success!!, color = colors.success, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }

    if (showCreate) {
        CreateRedeemDialog(
            onDismiss = { showCreate = false },
            onCreate = { type, amount, uses, plan, days ->
                scope.launch {
                    val result = service.createRedeemCode(type, amount, uses, plan, days, adminUid)
                    showCreate = false
                    if (result.isSuccess) {
                        val newCode = result.getOrNull()
                        if (newCode != null) {
                            lastCreatedCode = newCode
                            success = "Code created"
                        }
                        refresh()
                    } else {
                        error = result.exceptionOrNull()?.message
                    }
                }
            },
        )
    }

    val created = lastCreatedCode
    if (created != null) {
        CreatedCodeDialog(
            code = created,
            onDismiss = { lastCreatedCode = null },
            onCopy = { copyCode(created) },
        )
    }
}

@Composable
private fun CreateRedeemDialog(
    onDismiss: () -> Unit,
    onCreate: (type: String, amount: Int, uses: Int, plan: String, days: Int) -> Unit,
) {
    val colors = LocalSanjiColors.current
    var type by remember { mutableStateOf("credits") }
    var amount by remember { mutableStateOf("") }
    var uses by remember { mutableStateOf("1") }
    var plan by remember { mutableStateOf("pro") }
    var days by remember { mutableStateOf("30") }

    val canSubmit = uses.toIntOrNull()?.let { it > 0 } == true && when (type) {
        "credits", "coins" -> (amount.toIntOrNull() ?: 0) > 0
        "subscription" -> plan.isNotBlank() && (days.toIntOrNull() ?: 0) > 0
        else -> false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val amt = amount.toIntOrNull() ?: 0
                    val u = uses.toIntOrNull() ?: 1
                    val d = days.toIntOrNull() ?: 30
                    onCreate(type, amt, u, plan, d)
                },
                enabled = canSubmit,
            ) { Text("Create", color = colors.primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colors.onSurfaceVariant) }
        },
        containerColor = colors.surface,
        titleContentColor = colors.onBackground,
        textContentColor = colors.onBackground,
        title = { Text("Create Redeem Code") },
        text = {
            Column {
                RadioRow("💰 Credits", type == "credits") { type = "credits" }
                RadioRow("🪙 Coins", type == "coins") { type = "coins" }
                RadioRow("📦 Subscription", type == "subscription") { type = "subscription" }

                Spacer(Modifier.height(8.dp))

                if (type == "credits" || type == "coins") {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter { c -> c.isDigit() } },
                        label = { Text("Amount") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = plan,
                        onValueChange = { plan = it.lowercase() },
                        label = { Text("Plan id (pro / elite)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = days,
                        onValueChange = { days = it.filter { c -> c.isDigit() } },
                        label = { Text("Days") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = uses,
                    onValueChange = { uses = it.filter { c -> c.isDigit() } },
                    label = { Text("Uses limit") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun CreatedCodeDialog(
    code: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onCopy()
                onDismiss()
            }) { Text("Copy & Close", color = colors.primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = colors.onSurfaceVariant) }
        },
        containerColor = colors.surface,
        titleContentColor = colors.onBackground,
        textContentColor = colors.onBackground,
        title = { Text("Code Created") },
        text = {
            Column {
                Text(
                    "Share this code with the user:",
                    fontSize = 12.sp, color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.background,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        code,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        color = colors.primary, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(14.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalSanjiColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 13.sp, color = colors.onBackground)
    }
}