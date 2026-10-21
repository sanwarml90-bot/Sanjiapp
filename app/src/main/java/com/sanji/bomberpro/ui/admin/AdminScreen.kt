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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlusOne
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.data.FirebaseNode
import com.sanji.bomberpro.services.AdminService
import com.sanji.bomberpro.services.AdminUser
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch

private enum class AdminView { HOME, FIREBASE, USERS, USER_DETAIL, ROLES }

@Composable
fun AdminScreen(
    adminUid: String,
    adminEmail: String,
    isOwner: Boolean,
    onExit: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val service = remember { AdminService() }

    var view by remember { mutableStateOf(AdminView.HOME) }

    var firebases by remember { mutableStateOf<List<FirebaseNode>>(emptyList()) }
    var loadingFb by remember { mutableStateOf(false) }
    var showAddFb by remember { mutableStateOf(false) }

    var users by remember { mutableStateOf<List<AdminUser>>(emptyList()) }
    var userQuery by remember { mutableStateOf("") }
    var loadingUsers by remember { mutableStateOf(false) }

    var selectedUser by remember { mutableStateOf<AdminUser?>(null) }

    var error by remember { mutableStateOf<String?>(null) }

    fun loadFirebases() {
        scope.launch {
            loadingFb = true
            firebases = service.listFirebases()
            loadingFb = false
        }
    }

    fun loadUsers() {
        scope.launch {
            loadingUsers = true
            users = service.searchUsers(userQuery)
            loadingUsers = false
        }
    }

    when (view) {
        AdminView.HOME -> AdminHomeMenu(
            isOwner = isOwner,
            onOpenFirebase = { loadFirebases(); view = AdminView.FIREBASE },
            onOpenUsers = { loadUsers(); view = AdminView.USERS },
            onOpenRoles = { view = AdminView.ROLES },
            onExit = onExit,
        )

        AdminView.FIREBASE -> Column(
            modifier = Modifier.fillMaxSize().background(colors.background).padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { view = AdminView.HOME }) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
                }
                Text(
                    "Manage Firebases",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = colors.onBackground, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { loadFirebases() }) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = colors.primary)
                }
                IconButton(onClick = { showAddFb = true }) {
                    Icon(Icons.Default.PlusOne, "Add", tint = colors.primary)
                }
            }

            Spacer(Modifier.height(8.dp))

            if (loadingFb) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primary)
                }
            } else if (firebases.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No firebases", fontSize = 14.sp, color = colors.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { showAddFb = true },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.Black),
                        ) { Text("Add Firebase") }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(firebases) { fb ->
                        val idx = firebases.indexOf(fb) + 1
                        Surface(shape = RoundedCornerShape(14.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Firebase $idx",
                                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                            color = colors.onBackground,
                                        )
                                        Text(fb.label, fontSize = 11.sp, color = colors.onSurfaceVariant)
                                        if (isOwner) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                fb.url,
                                                fontSize = 10.sp, color = colors.onSurfaceVariant,
                                                fontFamily = FontFamily.Monospace, maxLines = 2,
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = fb.enabled,
                                        onCheckedChange = { checked ->
                                            scope.launch {
                                                service.toggleFirebase(fb.id, checked)
                                                loadFirebases()
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = colors.primary,
                                            checkedTrackColor = colors.primary.copy(alpha = 0.4f),
                                        ),
                                    )
                                    IconButton(onClick = {
                                        scope.launch {
                                            service.deleteFirebase(fb.id)
                                            loadFirebases()
                                        }
                                    }) {
                                        Icon(Icons.Default.Delete, "Delete", tint = colors.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showAddFb) {
                AddFirebaseDialog(
                    onDismiss = { showAddFb = false },
                    onConfirm = { url, label ->
                        scope.launch {
                            val result = service.addFirebase(url, label)
                            if (result.isSuccess) { showAddFb = false; loadFirebases() }
                            else error = result.exceptionOrNull()?.message
                        }
                    },
                    error = error,
                )
            }
        }

        AdminView.USERS -> Column(
            modifier = Modifier.fillMaxSize().background(colors.background).padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { view = AdminView.HOME }) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
                }
                Text(
                    "Users (${users.size})",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = colors.onBackground, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { loadUsers() }) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = colors.primary)
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = userQuery,
                onValueChange = { userQuery = it; loadUsers() },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = colors.onSurfaceVariant) },
                placeholder = { Text("Search by name / email / uid") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            if (loadingUsers) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primary)
                }
            } else if (users.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No users found", fontSize = 14.sp, color = colors.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(users) { u ->
                        Surface(
                            onClick = { selectedUser = u; view = AdminView.USER_DETAIL },
                            shape = RoundedCornerShape(12.dp),
                            color = colors.surface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            u.displayName,
                                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                            color = colors.onBackground,
                                        )
                                        if (u.banned) {
                                            Spacer(Modifier.width(6.dp))
                                            Text("🚫", fontSize = 12.sp)
                                        }
                                        if (u.role != "user") {
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                u.role.replace('_', ' ').uppercase(),
                                                fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                                color = colors.primary,
                                            )
                                        }
                                    }
                                    Text(u.email, fontSize = 10.sp, color = colors.onSurfaceVariant, maxLines = 1)
                                    Text(
                                        "UID: ${u.uid}",
                                        fontSize = 9.sp, color = colors.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                                Text(
                                    "💰${u.credits} 🪙${u.coins}",
                                    fontSize = 10.sp, color = colors.primary, fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }

        AdminView.USER_DETAIL -> {
            val u = selectedUser
            if (u == null) view = AdminView.USERS
            else UserDetailSection(
                user = u,
                adminUid = adminUid,
                isOwner = isOwner,
                service = service,
                onBack = { view = AdminView.USERS },
                onRefresh = {
                    scope.launch {
                        val fresh = service.getUserDetail(u.uid)
                        if (fresh != null) selectedUser = fresh
                    }
                },
            )
        }

        AdminView.ROLES -> {
            if (isOwner) {
                RolesScreen(
                    adminUid = adminUid,
                    adminEmail = adminEmail,
                    service = service,
                    onBack = { view = AdminView.HOME },
                )
            } else {
                view = AdminView.HOME
            }
        }
    }
}

@Composable
private fun AdminHomeMenu(
    isOwner: Boolean,
    onOpenFirebase: () -> Unit,
    onOpenUsers: () -> Unit,
    onOpenRoles: () -> Unit,
    onExit: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit) {
                Icon(Icons.Default.ArrowBack, "Exit", tint = colors.onBackground)
            }
            Text(
                "Admin Panel",
                fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = colors.onBackground, modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(16.dp))

        AdminMenuCard(
            emoji = "🔥",
            title = "Manage Firebases",
            subtitle = "Add / remove / enable realtime DB nodes",
            onClick = onOpenFirebase,
        )
        AdminMenuCard(
            emoji = "👥",
            title = "Users",
            subtitle = "Search, view, credits, ban, limits, notes, tags, subscription",
            onClick = onOpenUsers,
        )
        if (isOwner) {
            AdminMenuCard(
                emoji = "🔱",
                title = "Roles & Admins",
                subtitle = "Add/remove super admins and admins · transfer ownership",
                onClick = onOpenRoles,
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "More coming in next sessions",
            fontSize = 11.sp, color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Settings · Broadcast · Redeem Codes · Pricing · Logs · Giveaways · Analytics",
            fontSize = 10.sp, color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AdminMenuCard(
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                Text(subtitle, fontSize = 11.sp, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AddFirebaseDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    error: String?,
) {
    val colors = LocalSanjiColors.current
    var url by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (url.isNotBlank()) onConfirm(url.trim(), label.trim()) },
                enabled = url.isNotBlank(),
            ) { Text("Add", color = colors.primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colors.onSurfaceVariant) }
        },
        containerColor = colors.surface,
        titleContentColor = colors.onBackground,
        textContentColor = colors.onBackground,
        title = { Text("Add Firebase") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Firebase URL") },
                    placeholder = { Text("https://xxx.firebaseio.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = colors.error, fontSize = 11.sp)
                }
            }
        },
    )
}