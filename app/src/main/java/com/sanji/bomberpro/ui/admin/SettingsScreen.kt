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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.services.SettingsService
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    service: SettingsService,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    var editKey by remember { mutableStateOf<String?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editCurrent by remember { mutableStateOf("") }
    var editNumeric by remember { mutableStateOf(false) }
    var editMultiline by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            loading = true
            settings = service.getSettingsMap()
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun openEdit(key: String, title: String, numeric: Boolean = false, multiline: Boolean = false) {
        editKey = key
        editTitle = title
        editCurrent = (settings[key] ?: "").toString()
        editNumeric = numeric
        editMultiline = multiline
    }

    fun saveEdit(key: String, value: String) {
        scope.launch {
            val parsed: Any? = when {
                editNumeric -> value.toLongOrNull() ?: 0L
                editMultiline -> value
                else -> value
            }
            val ok = service.updateSetting(key, parsed)
            if (ok) { success = "$editTitle saved"; refresh() } else error = "Failed"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
            }
            Text(
                "Settings",
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = colors.onBackground, modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
            return
        }

        val otpCost = (settings["otpCost"] as? Number)?.toInt() ?: 1
        val dailyBonus = (settings["dailyBonusCredits"] as? Number)?.toInt() ?: 2
        val refCredits = (settings["refCredits"] as? Number)?.toInt() ?: 3
        val refCoins = (settings["refCoins"] as? Number)?.toInt() ?: 1
        val upiId = settings["upiId"] as? String ?: ""
        val upiName = settings["upiName"] as? String ?: ""
        val maint = settings["maintenance"] as? Boolean ?: false
        val maintMsg = settings["maintenanceMsg"] as? String ?: ""
        val latestVer = settings["latestVersion"] as? String ?: "1.0.0"
        val minVer = settings["minVersion"] as? String ?: "1.0.0"
        val apkUrl = settings["apkUrl"] as? String ?: ""
        val changelog = settings["changelog"] as? String ?: ""
        val forceUpdate = settings["forceUpdate"] as? Boolean ?: false

        Section("💳 Payment")
        SettingRow("UPI ID", if (upiId.isBlank()) "(not set)" else upiId) { openEdit("upiId", "UPI ID") }
        SettingRow("UPI Name", if (upiName.isBlank()) "(not set)" else upiName) { openEdit("upiName", "UPI Name") }

        Section("💰 Economy")
        SettingRow("OTP Cost", "$otpCost coins") { openEdit("otpCost", "OTP Cost", numeric = true) }
        SettingRow("Daily Bonus", "$dailyBonus credits") { openEdit("dailyBonusCredits", "Daily Bonus", numeric = true) }
        SettingRow("Referral Credits", "$refCredits") { openEdit("refCredits", "Referral Credits", numeric = true) }
        SettingRow("Referral Coins", "$refCoins") { openEdit("refCoins", "Referral Coins", numeric = true) }

        Section("🔧 Maintenance")
        ToggleRow("Maintenance Mode", maint) { checked ->
            scope.launch {
                service.updateSetting("maintenance", checked)
                refresh()
            }
        }
        SettingRow("Maintenance Message", maintMsg.ifBlank { "(default)" }) { openEdit("maintenanceMsg", "Maintenance Message", multiline = true) }

        Section("📱 App Update")
        SettingRow("Latest Version", latestVer) { openEdit("latestVersion", "Latest Version") }
        SettingRow("Min Version", minVer) { openEdit("minVersion", "Min Version") }
        SettingRow("APK URL", if (apkUrl.isBlank()) "(not set)" else apkUrl.take(30)) { openEdit("apkUrl", "APK URL") }
        SettingRow("Changelog", if (changelog.isBlank()) "(empty)" else changelog.take(40)) { openEdit("changelog", "Changelog", multiline = true) }
        ToggleRow("Force Update", forceUpdate) { checked ->
            scope.launch {
                service.updateSetting("forceUpdate", checked)
                refresh()
            }
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = colors.error, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        if (success != null) {
            Spacer(Modifier.height(12.dp))
            Text(success!!, color = colors.success, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(32.dp))
    }

    val key = editKey
    if (key != null) {
        TextEditDialog(
            title = editTitle,
            current = editCurrent,
            numeric = editNumeric,
            multiline = editMultiline,
            onDismiss = { editKey = null },
            onSave = { value ->
                editKey = null
                saveEdit(key, value)
            },
        )
    }
}

@Composable
private fun Section(text: String) {
    val colors = LocalSanjiColors.current
    Spacer(Modifier.height(12.dp))
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    val colors = LocalSanjiColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, color = colors.onBackground, modifier = Modifier.weight(1f))
            Text(value, fontSize = 11.sp, color = colors.primary, maxLines = 1, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = LocalSanjiColors.current
    Surface(shape = RoundedCornerShape(10.dp), color = colors.surface, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, color = colors.onBackground, modifier = Modifier.weight(1f))
            Switch(
                checked = value,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.primary,
                    checkedTrackColor = colors.primary.copy(alpha = 0.4f),
                ),
            )
        }
    }
}

@Composable
private fun TextEditDialog(
    title: String,
    current: String,
    numeric: Boolean,
    multiline: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val colors = LocalSanjiColors.current
    var text by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text("Save", color = colors.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colors.onSurfaceVariant) }
        },
        containerColor = colors.surface,
        titleContentColor = colors.onBackground,
        textContentColor = colors.onBackground,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = if (numeric) it.filter { c -> c.isDigit() } else it
                },
                singleLine = !multiline,
                minLines = if (multiline) 4 else 1,
                maxLines = if (multiline) 8 else 1,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                ),
            )
        },
    )
}