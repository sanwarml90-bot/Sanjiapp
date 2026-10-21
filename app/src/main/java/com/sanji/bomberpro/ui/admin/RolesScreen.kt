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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlusOne
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WorkspacePremium
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
import com.sanji.bomberpro.services.AdminService
import com.sanji.bomberpro.services.RoleEntry
import com.sanji.bomberpro.services.RolesList
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FMT_SHORT = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
private fun fmtShort(ts: Long): String =
    try { FMT_SHORT.format(Date(ts * 1000L)) } catch (_: Exception) { "—" }

@Composable
fun RolesScreen(
    adminUid: String,
    adminEmail: String,
    service: AdminService,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()

    var roles by remember { mutableStateOf<RolesList?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    var showAddSuper by remember { mutableStateOf(false) }
    var showAddAdmin by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }

    suspend fun refresh() {
        loading = true
        roles = service.listRoles()
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

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
                "Roles & Admins",
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = colors.onBackground, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { scope.launch { refresh() } }) {
                Icon(Icons.Default.Refresh, "Refresh", tint = colors.primary)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (loading || roles == null) {
            Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
            return
        }

        val r = roles!!

        // Owner card
        SectionTitle("👑 Owner")
        Spacer(Modifier.height(6.dp))
        Surface(shape = RoundedCornerShape(14.dp), color = colors.primary.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WorkspacePremium, null, tint = colors.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(r.owner, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                    Text("Full access · cannot be removed", fontSize = 10.sp, color = colors.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Super Admins
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔱 Super Admins (${r.superAdmins.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, modifier = Modifier.weight(1f))
            IconButton(onClick = { showAddSuper = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.PlusOne, "Add", tint = colors.primary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        if (r.superAdmins.isEmpty()) {
            Text("No super admins yet", fontSize = 11.sp, color = colors.onSurfaceVariant)
        } else {
            r.superAdmins.forEach { entry ->
                RoleCard(entry, onRemove = {
                    scope.launch {
                        val result = service.removeRole(entry.email, adminUid)
                        if (result.isSuccess) { success = "Removed ${entry.email}"; refresh() }
                        else error = result.exceptionOrNull()?.message
                    }
                })
            }
        }

        Spacer(Modifier.height(16.dp))

        // Admins
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🛡 Admins (${r.admins.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, modifier = Modifier.weight(1f))
            IconButton(onClick = { showAddAdmin = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.PlusOne, "Add", tint = colors.primary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        if (r.admins.isEmpty()) {
            Text("No admins yet", fontSize = 11.sp, color = colors.onSurfaceVariant)
        } else {
            r.admins.forEach { entry ->
                RoleCard(entry, onRemove = {
                    scope.launch {
                        val result = service.removeRole(entry.email, adminUid)
                        if (result.isSuccess) { success = "Removed ${entry.email}"; refresh() }
                        else error = result.exceptionOrNull()?.message
                    }
                })
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { showTransfer = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.error.copy(alpha = 0.15f),
                contentColor = colors.error,
            ),
        ) { Text("⚠️ Transfer Ownership", fontWeight = FontWeight.Bold) }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = colors.error, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        if (success != null) {
            Spacer(Modifier.height(12.dp))
            Text(success!!, color = colors.success, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "⚠️ Roles take effect on next sign-in of the user.",
            fontSize = 10.sp, color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }

    // Dialogs
    if (showAddSuper) AddRoleDialog(
        title = "Add Super Admin",
        onDismiss = { showAddSuper = false },
        onConfirm = { email ->
            scope.launch {
                val result = service.addRole(email, "super_admin", adminEmail, adminUid)
                showAddSuper = false
                if (result.isSuccess) { success = "Added super admin: $email"; refresh() }
                else error = result.exceptionOrNull()?.message
            }
        },
    )
    if (showAddAdmin) AddRoleDialog(
        title = "Add Admin",
        onDismiss = { showAddAdmin = false },
        onConfirm = { email ->
            scope.launch {
                val result = service.addRole(email, "admin", adminEmail, adminUid)
                showAddAdmin = false
                if (result.isSuccess) { success = "Added admin: $email"; refresh() }
                else error = result.exceptionOrNull()?.message
            }
        },
    )
    if (showTransfer) TransferDialog(
        currentOwner = roles?.owner ?: "",
        onDismiss = { showTransfer = false },
        onConfirm = { newEmail ->
            scope.launch {
                val result = service.transferOwnership(newEmail, adminUid)
                showTransfer = false
                if (result.isSuccess) {
                    success = "Ownership transferred to $newEmail — restart app"
                    // Invalidate owner cache
                    // Note: AuthManager instance is in MainActivity scope; not accessible here.
                    // User must restart app.
                    refresh()
                } else error = result.exceptionOrNull()?.message
            }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    val colors = LocalSanjiColors.current
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
}

@Composable
private fun RoleCard(entry: RoleEntry, onRemove: () -> Unit) {
    val colors = LocalSanjiColors.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.email,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = colors.onBackground, fontFamily = FontFamily.Monospace,
                )
                Text(
                    "Added ${fmtShort(entry.addedAt)} · by ${entry.addedBy.ifBlank { "—" }}",
                    fontSize = 10.sp, color = colors.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, "Remove", tint = colors.error)
            }
        }
    }
}

@Composable
private fun AddRoleDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val colors = LocalSanjiColors.current
    var email by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (email.contains("@")) onConfirm(email.trim().lowercase()) },
                enabled = email.contains("@"),
            ) { Text("Add", color = colors.primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colors.onSurfaceVariant) }
        },
        containerColor = colors.surface,
        titleContentColor = colors.onBackground,
        textContentColor = colors.onBackground,
        title = { Text(title) },
        text = {
            Column {
                Text("Enter the user's email (must be same as their Google login).", fontSize = 11.sp, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
            }
        },
    )
}

@Composable
private fun TransferDialog(
    currentOwner: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val colors = LocalSanjiColors.current
    var email by remember { mutableStateOf("") }
    var confirmText by remember { mutableStateOf("") }
    val canConfirm = email.contains("@") && confirmText == "TRANSFER"

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(email.trim().lowercase()) },
                enabled = canConfirm,
            ) { Text("TRANSFER", color = colors.error, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colors.onSurfaceVariant) }
        },
        containerColor = colors.surface,
        titleContentColor = colors.onBackground,
        textContentColor = colors.onBackground,
        title = { Text("⚠️ Transfer Ownership") },
        text = {
            Column {
                Text(
                    "Current owner: $currentOwner\n\n" +
                            "You will become super_admin. New owner gets full control.\n" +
                            "New owner must sign in after transfer.",
                    fontSize = 11.sp, color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("New owner email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmText,
                    onValueChange = { confirmText = it },
                    label = { Text("Type TRANSFER to confirm") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}