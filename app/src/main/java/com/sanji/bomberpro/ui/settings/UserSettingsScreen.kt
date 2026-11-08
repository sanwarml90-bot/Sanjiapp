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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.data.AuthManager
import com.sanji.bomberpro.data.UserProfile
import com.sanji.bomberpro.services.ProtectionService
import com.sanji.bomberpro.theme.AppTheme
import com.sanji.bomberpro.theme.LocalSanjiColors
import com.sanji.bomberpro.theme.ThemeManager
import com.sanji.bomberpro.ui.components.SanjiEmoji
import com.sanji.bomberpro.ui.components.fadeSlideIn
import com.sanji.bomberpro.ui.components.pressScale
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
    val currentTheme by themeManager.currentTheme.collectAsState(initial = themeManager.defaultTheme)
    val protectionService = remember { ProtectionService() }

    var showThemePicker by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showUsernameEdit by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showProtectDialog by remember { mutableStateOf(false) }
    var showUnprotectConfirm by remember { mutableStateOf(false) }

    var deleteConfirmText by remember { mutableStateOf("") }
    var protectInput by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    var protecting by remember { mutableStateOf(false) }
    var usernameInput by remember { mutableStateOf(profile?.username ?: "") }
    var currentProtected by remember { mutableStateOf(profile?.protectedNumber ?: "") }

    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var savingUsername by remember { mutableStateOf(false) }

    val canProtect = profile?.premium == true || profile?.role == "owner"

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

    LaunchedEffect(error) {
        if (error != null) {
            kotlinx.coroutines.delay(4000)
            error = null
        }
    }
    LaunchedEffect(success) {
        if (success != null) {
            kotlinx.coroutines.delay(2500)
            success = null
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
                    "${SanjiEmoji.SETTINGS} Settings",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = colors.onBackground, modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))

            // Profile card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surface,
                modifier = Modifier.fillMaxWidth().fadeSlideIn(delayMs = 0),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(colors.primary.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Person, null, tint = colors.primary, modifier = Modifier.size(28.dp))
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
                    Spacer(Modifier.height(12.dp))
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
                                    "${SanjiEmoji.KEY} Your User ID",
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
                    Surface(
                        onClick = {
                            usernameInput = profile?.username ?: ""
                            showUsernameEdit = true
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = colors.primary.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth().pressScale(),
                    ) {
                        Text(
                            "${SanjiEmoji.EDIT} Change Username",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
                }
            }

            // ============================================================
            //  PROTECTION CARD — premium users + owner only
            // ============================================================
            if (canProtect) {
                Spacer(Modifier.height(20.dp))
                SectionLabel("🛡️ NUMBER PROTECTION")
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (currentProtected.isNotBlank()) colors.success.copy(alpha = 0.10f) else colors.surface,
                    modifier = Modifier.fillMaxWidth().fadeSlideIn(delayMs = 60),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, null, tint = colors.primary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Protect Your Number",
                                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                    color = colors.onBackground,
                                )
                                Text(
                                    "Protected numbers cannot be bombed",
                                    fontSize = 11.sp, color = colors.onSurfaceVariant,
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        if (currentProtected.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = colors.background,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${SanjiEmoji.SUCCESS} Protected",
                                            fontSize = 10.sp, color = colors.success, fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            currentProtected,
                                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                            color = colors.onBackground,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                    TextButton(onClick = { showUnprotectConfirm = true }) {
                                        Text("Remove", color = colors.error, fontSize = 12.sp)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "You can protect only ONE number at a time. Remove current to change.",
                                fontSize = 10.sp, color = colors.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                "Add your number to stop all blasts on it. Only 1 number at a time.",
                                fontSize = 11.sp, color = colors.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    protectInput = ""
                                    showProtectDialog = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary,
                                    contentColor = Color.Black,
                                ),
                                modifier = Modifier.fillMaxWidth().height(44.dp).pressScale(),
                            ) {
                                Text("${SanjiEmoji.SHIELD} Protect Number", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
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
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).pressScale()
                    .fadeSlideIn(delayMs = 80),
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
                            currentTheme.displayName,
                            fontSize = 11.sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Text(
                        "${themeManager.allThemes.size}",
                        fontSize = 12.sp, color = colors.primary, fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Support
            SectionLabel("SUPPORT")
            Surface(
                onClick = { openTelegram("https://t.me/sanjipro5") },
                shape = RoundedCornerShape(14.dp),
                color = colors.surface,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).pressScale()
                    .fadeSlideIn(delayMs = 140),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Send, null, tint = colors.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${SanjiEmoji.MESSAGE} Contact Admin",
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
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).fadeSlideIn(delayMs = 200),
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
                modifier = Modifier.fillMaxWidth().height(52.dp).pressScale().fadeSlideIn(delayMs = 260),
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

            Spacer(Modifier.height(10.dp))

            // Delete
            Button(
                onClick = {
                    deleteConfirmText = ""
                    showDeleteConfirm = true
                },
                modifier = Modifier.fillMaxWidth().height(52.dp).pressScale().fadeSlideIn(delayMs = 300),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = colors.error,
                ),
            ) {
                Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Delete My Account", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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

    // ---- PROTECT DIALOG ----
    if (showProtectDialog) {
        AlertDialog(
            onDismissRequest = { if (!protecting) showProtectDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (protecting) return@TextButton
                        protecting = true
                        error = null
                        scope.launch {
                            val uid = profile?.uid ?: ""
                            val name = profile?.displayName ?: "User"
                            val res = protectionService.protectNumber(uid, name, protectInput)
                            protecting = false
                            if (res.isSuccess) {
                                currentProtected = protectionService.cleanNumber(protectInput)
                                success = "✅ Number protected: $currentProtected"
                                showProtectDialog = false
                                authManager.refreshProfile()
                            } else {
                                error = res.exceptionOrNull()?.message ?: "Failed"
                            }
                        }
                    },
                    enabled = !protecting && protectInput.isNotBlank(),
                ) {
                    if (protecting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.primary)
                    } else {
                        Text("PROTECT", color = colors.primary, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showProtectDialog = false }, enabled = !protecting) {
                    Text("Cancel", color = colors.onSurfaceVariant)
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.onBackground,
            textContentColor = colors.onBackground,
            title = { Text("${SanjiEmoji.SHIELD} Protect Number") },
            text = {
                Column {
                    Text(
                        "Enter your phone number. This number will be blocked from all blasts.",
                        fontSize = 11.sp, color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = protectInput,
                        onValueChange = { protectInput = it.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(15) },
                        label = { Text("Phone number") },
                        placeholder = { Text("+91 9876543210") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !protecting,
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.warning.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "⚠️ Only ONE number can be protected at a time. You can remove later and change.",
                            fontSize = 10.sp, color = colors.onBackground,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            },
        )
    }

    // ---- UNPROTECT CONFIRM ----
    if (showUnprotectConfirm) {
        AlertDialog(
            onDismissRequest = { showUnprotectConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showUnprotectConfirm = false
                    scope.launch {
                        val uid = profile?.uid ?: ""
                        val res = protectionService.unprotectNumber(uid)
                        if (res.isSuccess) {
                            currentProtected = ""
                            success = "Protection removed"
                            authManager.refreshProfile()
                        } else {
                            error = res.exceptionOrNull()?.message ?: "Failed"
                        }
                    }
                }) {
                    Text("Remove", color = colors.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnprotectConfirm = false }) {
                    Text("Cancel", color = colors.onSurfaceVariant)
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.onBackground,
            textContentColor = colors.onBackground,
            title = { Text("Remove Protection?") },
            text = {
                Text(
                    "This number will no longer be protected. Anyone can blast it.",
                    fontSize = 12.sp,
                )
            },
        )
    }

    // ---- THEME PICKER ----
    if (showThemePicker) {
        ThemePickerDialog(
            current = currentTheme,
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

    // ---- LOGOUT ----
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

    // ---- USERNAME EDIT ----
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

    // ---- DELETE ACCOUNT ----
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteConfirm = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (deleteConfirmText != "DELETE") return@TextButton
                        deleting = true
                        error = null
                        scope.launch {
                            val res = authManager.deleteOwnAccount()
                            deleting = false
                            if (res.isSuccess) {
                                showDeleteConfirm = false
                                onSignedOut()
                            } else {
                                error = res.exceptionOrNull()?.message ?: "Delete failed"
                                showDeleteConfirm = false
                            }
                        }
                    },
                    enabled = !deleting && deleteConfirmText == "DELETE",
                ) {
                    if (deleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.error,
                        )
                    } else {
                        Text("Delete Forever", color = colors.error, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    enabled = !deleting,
                ) { Text("Cancel", color = colors.onSurfaceVariant) }
            },
            containerColor = colors.surface,
            titleContentColor = colors.onBackground,
            textContentColor = colors.onBackground,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(SanjiEmoji.WARNING, fontSize = 18.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete Account?")
                }
            },
            text = {
                Column {
                    Text(
                        "This will permanently remove your account, release your device lock, and sign you out.",
                        fontSize = 12.sp,
                        color = colors.onBackground,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Blast history and transactions will be archived. Credits and coins will be lost.",
                        fontSize = 11.sp,
                        color = colors.error,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Type DELETE to confirm:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.error,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = deleteConfirmText,
                        onValueChange = { deleteConfirmText = it.uppercase() },
                        placeholder = { Text("DELETE") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !deleting,
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
        title = { Text("${SanjiEmoji.SPARKLE} Choose Theme") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                all.forEach { theme ->
                    val isSelected = theme.id == current.id
                    Surface(
                        onClick = { onSelect(theme) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) colors.primary.copy(alpha = 0.12f) else colors.background,
                        modifier = Modifier.fillMaxWidth().pressScale(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(16.dp).background(theme.primary, CircleShape))
                                Box(modifier = Modifier.size(16.dp).background(theme.secondary, CircleShape))
                                Box(modifier = Modifier.size(16.dp).background(theme.accent, CircleShape))
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