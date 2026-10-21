package com.sanji.bomberpro.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PlusOne
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Toll
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
import com.sanji.bomberpro.data.FirebaseNode
import com.sanji.bomberpro.services.AdminService
import com.sanji.bomberpro.services.AdminUser
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AdminView { HOME, FIREBASE, USERS, USER_DETAIL }

private val FMT_SHORT = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
private fun fmtShort(ts: Long): String =
    try { FMT_SHORT.format(Date(ts * 1000L)) } catch (_: Exception) { "—" }

@Composable
fun AdminScreen(
    adminUid: String,
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
    var success by remember { mutableStateOf<String?>(null) }

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
            onOpenFirebase = {
                loadFirebases()
                view = AdminView.FIREBASE
            },
            onOpenUsers = {
                loadUsers()
                view = AdminView.USERS
            },
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
                                        Text(
                                            fb.label,
                                            fontSize = 11.sp, color = colors.onSurfaceVariant,
                                        )
                                        if (isOwner) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                fb.url,
                                                fontSize = 10.sp, color = colors.onSurfaceVariant,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 2,
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
                            if (result.isSuccess) {
                                showAddFb = false
                                loadFirebases()
                            } else {
                                error = result.exceptionOrNull()?.message
                            }
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = userQuery,
                    onValueChange = {
                        userQuery = it
                        loadUsers()
                    },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = colors.onSurfaceVariant) },
                    placeholder = { Text("Search by name / email / uid") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

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
                            onClick = {
                                selectedUser = u
                                view = AdminView.USER_DETAIL
                            },
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
                                    Text(
                                        u.email,
                                        fontSize = 10.sp, color = colors.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                    Text(
                                        "UID: ${u.uid}",
                                        fontSize = 9.sp, color = colors.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                                Text(
                                    "💰${u.credits} 🪙${u.coins}",
                                    fontSize = 10.sp, color = colors.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }

        AdminView.USER_DETAIL -> {
            val u = selectedUser
            if (u == null) {
                view = AdminView.USERS
            } else {
                UserDetailSection(
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
        }
    }
}

@Composable
private fun AdminHomeMenu(
    onOpenFirebase: () -> Unit,
    onOpenUsers: () -> Unit,
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
            subtitle = "Search, view, adjust credits and coins",
            onClick = onOpenUsers,
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "More coming in next sessions",
            fontSize = 11.sp, color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Roles · Settings · Broadcast · Redeem Codes · Pricing · Logs · Giveaways · Analytics",
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

@Composable
private fun UserDetailSection(
    user: AdminUser,
    adminUid: String,
    isOwner: Boolean,
    service: AdminService,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()

    var showCreditsAdd by remember { mutableStateOf(false) }
    var showCreditsDeduct by remember { mutableStateOf(false) }
    var showCoinsAdd by remember { mutableStateOf(false) }
    var showCoinsDeduct by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

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
                "User Detail",
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = colors.onBackground, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, "Refresh", tint = colors.primary)
            }
        }

        Spacer(Modifier.height(12.dp))

        // User card
        Surface(shape = RoundedCornerShape(14.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                DetailRow("Name", user.displayName)
                DetailRow("Email", user.email)
                DetailRow("UID", user.uid)
                DetailRow("Role", user.role.replace('_', ' ').uppercase())
                DetailRow("Terms", if (user.termsAccepted) "✅ Accepted" else "❌ Pending")
                DetailRow("Banned", if (user.banned) "🚫 ${user.banReason.ifBlank { "yes" }}" else "No")
                DetailRow("Premium", if (user.premium) "💎 Yes" else "No")
                DetailRow("Plan", "${user.subscriptionPlan}${if (user.subscriptionExpiresAt > 0) " · till ${fmtShort(user.subscriptionExpiresAt)}" else ""}")
                DetailRow("Refer Code", user.referCode.ifBlank { "—" })
                DetailRow("Referred By", user.referredBy ?: "—")
                DetailRow("Joined", fmtShort(user.createdAt))
                DetailRow("Last Active", fmtShort(user.lastActive))
                DetailRow("Total SMS", "${user.totalSms}")
                DetailRow("Total OTPs", "${user.totalOtps}")
                DetailRow("Total Blasts", "${user.totalBlasts}")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Balances
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = colors.surface, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(14.dp)) {
                    Text("💰 Credits", fontSize = 11.sp, color = colors.onSurfaceVariant)
                    Text("${user.credits}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmallBtn("+", colors.success) { showCreditsAdd = true }
                        SmallBtn("−", colors.error) { showCreditsDeduct = true }
                    }
                }
            }
            Surface(shape = RoundedCornerShape(14.dp), color = colors.surface, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(14.dp)) {
                    Text("🪙 Coins", fontSize = 11.sp, color = colors.onSurfaceVariant)
                    Text("${user.coins}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmallBtn("+", colors.success) { showCoinsAdd = true }
                        SmallBtn("−", colors.error) { showCoinsDeduct = true }
                    }
                }
            }
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = colors.error, fontSize = 12.sp)
        }
        if (success != null) {
            Spacer(Modifier.height(12.dp))
            Text(success!!, color = colors.success, fontSize = 12.sp)
        }

        if (showCreditsAdd) {
            AmountDialog(
                title = "Add Credits",
                onDismiss = { showCreditsAdd = false },
                onConfirm = { amt ->
                    scope.launch {
                        val ok = service.addCreditsToUser(user.uid, amt, adminUid, "admin-add")
                        showCreditsAdd = false
                        if (ok) { success = "+$amt credits"; onRefresh() } else error = "Failed"
                    }
                },
            )
        }
        if (showCreditsDeduct) {
            AmountDialog(
                title = "Deduct Credits",
                onDismiss = { showCreditsDeduct = false },
                onConfirm = { amt ->
                    scope.launch {
                        val ok = service.deductCreditsFromUser(user.uid, amt, adminUid)
                        showCreditsDeduct = false
                        if (ok) { success = "-$amt credits"; onRefresh() } else error = "Failed — insufficient or error"
                    }
                },
            )
        }
        if (showCoinsAdd) {
            AmountDialog(
                title = "Add Coins",
                onDismiss = { showCoinsAdd = false },
                onConfirm = { amt ->
                    scope.launch {
                        val ok = service.addCoinsToUser(user.uid, amt, adminUid)
                        showCoinsAdd = false
                        if (ok) { success = "+$amt coins"; onRefresh() } else error = "Failed"
                    }
                },
            )
        }
        if (showCoinsDeduct) {
            AmountDialog(
                title = "Deduct Coins",
                onDismiss = { showCoinsDeduct = false },
                onConfirm = { amt ->
                    scope.launch {
                        val ok = service.deductCoinsFromUser(user.uid, amt, adminUid)
                        showCoinsDeduct = false
                        if (ok) { success = "-$amt coins"; onRefresh() } else error = "Failed — insufficient or error"
                    }
                },
            )
        }
    }
}

@Composable
private fun SmallBtn(label: String, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            label,
            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val colors = LocalSanjiColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 11.sp, color = colors.onSurfaceVariant)
        Text(
            value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = colors.onBackground, maxLines = 2, textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun AmountDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val colors = LocalSanjiColors.current
    var text by remember { mutableStateOf("") }
    val amt = text.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (amt > 0) onConfirm(amt) },
                enabled = amt > 0,
            ) { Text("Confirm", color = colors.primary, fontWeight = FontWeight.Bold) }
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
                onValueChange = { text = it.filter { c -> c.isDigit() } },
                label = { Text("Amount") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}