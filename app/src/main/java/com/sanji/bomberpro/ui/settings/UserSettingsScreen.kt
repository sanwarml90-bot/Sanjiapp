package com.sanji.bomberpro.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.data.AuthManager
import com.sanji.bomberpro.data.UserProfile
import com.sanji.bomberpro.theme.AppTheme
import com.sanji.bomberpro.theme.LocalSanjiColors
import com.sanji.bomberpro.theme.ThemeManager
import kotlinx.coroutines.launch

@Composable
fun UserSettingsScreen(
    authManager: AuthManager,
    themeManager: ThemeManager,
    profile: UserProfile?,
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showThemePicker by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showUsernameEdit by remember { mutableStateOf(false) }
    var usernameInput by remember { mutableStateOf(profile?.username ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var savingUsername by remember { mutableStateOf(false) }

    fun openTelegram(url: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            error = "No app to open Telegram"
        }
    }

    fun copyToClipboard(label: String, value: String) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(label, value))
            success = "Copied"
        } catch (_: Exception) {
            error = "Copy failed"
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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

            Spacer(Modifier.height(16.dp))

            // Profile card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(colors.primary.copy(alpha = 0.2f), RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Person, null, tint = colors.primary, modifier = Modifier.size(26.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                profile?.displayName?.ifBlank { "User" } ?: "User",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onBackground,
                            )
                            Text(
                                "@${profile?.username?.ifBlank { "user" } ?: "user"}",
                                fontSize = 12.sp,
                                color = colors.primary,
                            )
                            Text(
                                profile?.email ?: "",
                                fontSize = 11.sp,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // Own UID — copyable
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.background,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Your User ID",
                                    fontSize = 10.sp, color = colors.onSurfaceVariant,
                                )
                                Text(
                                    profile?.uid ?: "—",
                                    fontSize = 11.sp, color = colors.onBackground,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                )
                            }
                            IconButton(onClick = {
                                val uid = profile?.uid ?: return@IconButton
                                copyToClipboard("Your UID", uid)
                            }) {
                                Icon(
                                    Icons.Default.ContentCopy, "Copy UID",
                                    tint = colors.primary, modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            onClick = {
                                usernameInput = profile?.username ?: ""
                                showUsernameEdit = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = colors.primary.copy(alpha = 0.12f),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                "Change Username",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Appearance
            SectionLabel("APPEARANCE")
            Surface(
                onClick = { showThemePicker = true },
                shape = RoundedCornerShape(14.dp),
                color = colors.surface,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Palette, null, tint = colors.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Theme",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onBackground,
                        )
                        Text(
                            "Change app color scheme",
                            fontSize = 11.sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Text("6", fontSize = 12.sp, color = colors.primary, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(20.dp))

            // Contact / support
            SectionLabel("SUPPORT")
            Surface(
                onClick = { openTelegram("https://t.me/sanjipro5") },
                shape = RoundedCornerShape(14.dp),
                color = colors.surface,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Send, null, tint = colors.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Contact Admin",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onBackground,
                        )
                        Text(
                            "@sanjipro5",
                            fontSize = 11.sp,
                            color = colors.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // About
            SectionLabel("ABOUT")
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.surface,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Version", fontSize = 12.sp, color = colors.onSurfaceVariant)
                        Text("1.0.0", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Telegram", fontSize = 12.sp, color = colors.onSurfaceVariant)
                        Text("@sanjipro5", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Logout
            Button(
                onClick = { showLogoutConfirm = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.error.copy(alpha = 0.15f),
                    contentColor = colors.error,
                ),
            ) {
                Icon(Icons.Default.ExitToApp, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Log Out", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
    }

    // Theme picker dialog
    if (showThemePicker) {
        ThemePickerDialog(
            current = themeManager.defaultTheme,
            all = themeManager.allThemes,
            onSelect = { t ->
                scope.launch {
                    themeManager.setTheme(t)
                    showThemePicker = false
                }
            },
            onDismiss = { showThemePicker = false },
        )
    }

    // Logout confirm
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    authManager.signOut()
                    onSignedOut()
                }) { Text("Log Out", color = colors.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Cancel", color = colors.onSurfaceVariant)
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.onBackground,
            textContentColor = colors.onBackground,
            title = { Text("Log out?") },
            text = { Text("You'll need to sign in again to use the app.", fontSize = 12.sp) },
        )
    }

    // Username edit
    if (showUsernameEdit) {
        AlertDialog(
            onDismissRequest = { showUsernameEdit = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (savingUsername) return@TextButton
                        savingUsername = true
                        error = null
                        success = null
                        scope.launch {
                            val res = authManager.updateUsername(usernameInput)
                            savingUsername = false
                            if (res.isSuccess) {
                                success = "Username updated"
                                showUsernameEdit = false
                            } else {
                                error = res.exceptionOrNull()?.message ?: "Failed"
                            }
                        }
                    },
                    enabled = !savingUsername && usernameInput.length >= 3,
                ) { Text("Save", color = colors.primary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showUsernameEdit = false }) {
                    Text("Cancel", color = colors.onSurfaceVariant)
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.onBackground,
            textContentColor = colors.onBackground,
            title = { Text("Change Username") },
            text = {
                Column {
                    Text(
                        "Letters, numbers, and _ only. Min 3 chars. Must be unique.",
                        fontSize = 11.sp,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it.trim().take(32) },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
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
        modifier = Modifier.padding(bottom = 6.dp, start = 4.dp),
    )
}

@Composable
private fun ThemePickerDialog(
    current: AppTheme,
    all: List<AppTheme>,
    onSelect: (AppTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = colors.onSurfaceVariant) }
        },
        containerColor = colors.surface,
        titleContentColor = colors.onBackground,
        textContentColor = colors.onBackground,
        title = { Text("Choose Theme") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                all.forEach { theme ->
                    val isSelected = theme.id == current.id
                    Surface(
                        onClick = { onSelect(theme) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) colors.primary.copy(alpha = 0.12f) else colors.background,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(theme.primary, RoundedCornerShape(50))
                                )
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(theme.secondary, RoundedCornerShape(50))
                                )
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(theme.accent, RoundedCornerShape(50))
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                theme.displayName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onBackground,
                                modifier = Modifier.weight(1f),
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}