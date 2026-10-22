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
import com.sanji.bomberpro.services.SettingsService
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class AdminView {
    HOME, FIREBASE, FIREBASE_BULK, USERS, USER_DETAIL, ROLES,
    SETTINGS, BROADCAST, REDEEM, PRICING,
    LOGS, ANALYTICS,
    GIVEAWAY_LIST, GIVEAWAY_CREATE,
}

@Composable
fun AdminScreen(
    adminUid: String,
    adminEmail: String,
    adminName: String,
    isOwner: Boolean,
    onExit: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val adminService = remember { AdminService() }
    val settingsService = remember { SettingsService() }

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

    var showBulkAdd by remember { mutableStateOf(false) }
    var showBulkDeduct by remember { mutableStateOf(false) }

    fun loadFirebases() {
        scope.launch {
            loadingFb = true
            firebases = adminService.listFirebases()
            loadingFb = false
        }
    }

    LaunchedEffect(userQuery) {
        delay(350)
        loadingUsers = true
        users = adminService.searchUsers(userQuery)
        loadingUsers = false
    }

    LaunchedEffect(error) {
        if (error != null) { delay(4000); error = null }
    }
    LaunchedEffect(success) {
        if (success != null) { delay(4000); success = null }
    }

    when (view) {
        AdminView.HOME -> AdminHomeMenu(
            isOwner = isOwner,
            onOpenFirebase = { loadFirebases(); view = AdminView.FIREBASE },
            onOpenFirebaseBulk = { view = AdminView.FIREBASE_BULK },
            onOpenUsers = { view = AdminView.USERS },
            onOpenRoles = { view = AdminView.ROLES },
            onOpenSettings = { view = AdminView.SETTINGS },
            onOpenBroadcast = { view = AdminView.BROADCAST },
            onOpenRedeem = { view = AdminView.REDEEM },
            onOpenPricing = { view = AdminView.PRICING },
            onOpenLogs = { view = AdminView.LOGS },
            onOpenAnalytics = { view = AdminView.ANALYTICS },
            onOpenGiveaways = { view = AdminView.GIVEAWAY_LIST },
            onOpenBulkAdd = { showBulkAdd = true },
            onOpenBulkDeduct = { showBulkDeduct = true },
            onExit = onExit,
        )

        AdminView.GIVEAWAY_LIST -> GiveawayAdminScreen(
            adminUid = adminUid,
            onCreate = { view = AdminView.GIVEAWAY_CREATE },
            onBack = { view = AdminView.HOME },
        )

        AdminView.GIVEAWAY_CREATE -> GiveawayCreateScreen(
            adminUid = adminUid,
            onBack = { view = AdminView.GIVEAWAY_LIST },
            onCreated = {
                success = "Giveaway created"
                view = AdminView.GIVEAWAY_LIST
            },
        )

        AdminView.FIREBASE_BULK -> FirebaseBulkAddScreen(
            adminUid = adminUid,
            onBack = { view = AdminView.HOME },
            onCompleted = { loadFirebases() },
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
                                                adminService.toggleFirebase(fb.id, checked)
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
                                            adminService.deleteFirebase(fb.id)
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
                            val result = adminService.addFirebase(url, label)
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
                IconButton(onClick = {
                    scope.launch {
                        loadingUsers = true
                        users = adminService.searchUsers(userQuery)
                        loadingUsers = false
                    }
                }) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = colors.primary)
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = userQuery,
                onValueChange = { userQuery = it },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = colors.onSurfaceVariant) },
                placeholder = { Text("Search by name / email / uid / sim") },
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
                                        if (u.premium) {
                                            Spacer(Modifier.width(6.dp))
                                            Text("💎", fontSize = 12.sp)
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
                                    if (u.username.isNotBlank()) {
                                        Text("@${u.username}", fontSize = 10.sp, color = colors.primary)
                                    }
                                    Text(u.email, fontSize = 10.sp, color = colors.onSurfaceVariant, maxLines = 1)
                                    Text(
                                        "UID: ${u.uid}",
                                        fontSize = 9.sp, color = colors.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    if (u.sim1Number.isNotBlank() || u.sim2Number.isNotBlank()) {
                                        Text(
                                            "📱 ${listOfNotNull(u.sim1Number.takeIf { it.isNotBlank() }, u.sim2Number.takeIf { it.isNotBlank() }).joinToString(" / ")}",
                                            fontSize = 9.sp, color = colors.primary,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
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
            if (u == null) {
                LaunchedEffect(Unit) { view = AdminView.USERS }
            } else {
                UserDetailSection(
                    user = u,
                    adminUid = adminUid,
                    isOwner = isOwner,
                    service = adminService,
                    onBack = { view = AdminView.USERS },
                    onRefresh = {
                        scope.launch {
                            val fresh = adminService.getUserDetail(u.uid)
                            if (fresh != null) selectedUser = fresh
                        }
                    },
                )
            }
        }

        AdminView.ROLES -> {
            if (isOwner) {
                RolesScreen(
                    adminUid = adminUid,
                    adminEmail = adminEmail,
                    service = adminService,
                    onBack = { view = AdminView.HOME },
                )
            } else {
                LaunchedEffect(Unit) { view = AdminView.HOME }
            }
        }

        AdminView.SETTINGS -> {
            if (isOwner) {
                SettingsScreen(
                    onBack = { view = AdminView.HOME },
                    service = settingsService,
                )
            } else {
                LaunchedEffect(Unit) { view = AdminView.HOME }
            }
        }

        AdminView.BROADCAST -> BroadcastScreen(
            adminUid = adminUid,
            adminName = adminName,
            service = settingsService,
            onBack = { view = AdminView.HOME },
        )

        AdminView.REDEEM -> RedeemCodesScreen(
            adminUid = adminUid,
            service = settingsService,
            onBack = { view = AdminView.HOME },
        )

        AdminView.PRICING -> {
            if (isOwner) {
                PricingScreen(
                    service = settingsService,
                    onBack = { view = AdminView.HOME },
                )
            } else {
                LaunchedEffect(Unit) { view = AdminView.HOME }
            }
        }

        AdminView.LOGS -> LogsScreen(onBack = { view = AdminView.HOME })

        AdminView.ANALYTICS -> AnalyticsScreen(onBack = { view = AdminView.HOME })
    }

    if (showBulkAdd) {
        BulkAmountDialog(
            title = "Add credits to ALL users",
            subtitle = "Adds to every user's credits + manual pool",
            onDismiss = { showBulkAdd = false },
            onConfirm = { amount ->
                scope.launch {
                    val result = adminService.addCreditsToAll(amount, adminUid)
                    showBulkAdd = false
                    if (result.isSuccess) {
                        success = "+$amount credits → ${result.getOrNull()} users"
                    } else {
                        error = result.exceptionOrNull()?.message ?: "Failed"
                    }
                }
            },
        )
    }
    if (showBulkDeduct) {
        BulkAmountDialog(
            title = "Deduct credits from ALL users",
            subtitle = "Only cuts from admin-granted (manual) pool. Purchased credits untouched.",
            onDismiss = { showBulkDeduct = false },
            onConfirm = { amount ->
                scope.launch {
                    val result = adminService.deductCreditsFromAll(amount, adminUid)
                    showBulkDeduct = false
                    if (result.isSuccess) {
                        success = "-$amount credits from ${result.getOrNull()} users"
                    } else {
                        error = result.exceptionOrNull()?.message ?: "Failed"
                    }
                }
            },
        )
    }

    if (error != null || success != null) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (error != null) colors.error.copy(alpha = 0.95f) else colors.success.copy(alpha = 0.95f),
            ) {
                Text(
                    error ?: success ?: "",
                    fontSize = 12.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AdminHomeMenu(
    isOwner: Boolean,
    onOpenFirebase: () -> Unit,
    onOpenFirebaseBulk: () -> Unit,
    onOpenUsers: () -> Unit,
    onOpenRoles: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBroadcast: () -> Unit,
    onOpenRedeem: () -> Unit,
    onOpenPricing: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenGiveaways: () -> Unit,
    onOpenBulkAdd: () -> Unit,
    onOpenBulkDeduct: () -> Unit,
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

        AdminMenuCard("🔥", "Manage Firebases", "Add / remove / enable realtime DB nodes", onOpenFirebase)
        AdminMenuCard("📁", "Bulk Add Firebases", "Import from file or paste — validates URLs", onOpenFirebaseBulk)
        AdminMenuCard("👥", "Users", "Search, view, credits, ban, limits, notes, tags, subscription, SIM", onOpenUsers)
        if (isOwner) {
            AdminMenuCard("🔱", "Roles & Admins", "Add/remove super admins and admins · transfer ownership", onOpenRoles)
            AdminMenuCard("⚙️", "Settings", "OTP cost, referral rewards, UPI, maintenance, force update", onOpenSettings)
        }
        AdminMenuCard("🎁", "Giveaways", "Create, manage, end giveaways · vote adjust · leaderboard", onOpenGiveaways)
        AdminMenuCard("📢", "Broadcast", "Send message to all users", onOpenBroadcast)
        AdminMenuCard("🎟", "Redeem Codes", "Create / view / delete codes", onOpenRedeem)
        if (isOwner) {
            AdminMenuCard("💳", "Pricing", "Manage credit packs and subscription plans", onOpenPricing)
        }
        AdminMenuCard("📜", "Logs", "Activity · Audit · Payment history", onOpenLogs)
        AdminMenuCard("📊", "Analytics", "Users · Revenue · Daily stats", onOpenAnalytics)
        AdminMenuCard("💰", "Bulk Add Credits", "Add credits to every user at once", onOpenBulkAdd)
        AdminMenuCard("💸", "Bulk Deduct Credits", "Deduct from manual pool only · purchased credits untouched", onOpenBulkDeduct)

        Spacer(Modifier.height(24.dp))
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
private fun BulkAmountDialog(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val colors = LocalSanjiColors.current
    var text by remember { mutableStateOf("") }
    val amount = text.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (amount > 0) onConfirm(amount) },
                enabled = amount > 0,
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
            Column {
                Text(subtitle, fontSize = 11.sp, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() } },
                    label = { Text("Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}