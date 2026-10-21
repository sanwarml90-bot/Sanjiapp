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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlusOne
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.services.CreditPack
import com.sanji.bomberpro.services.SettingsService
import com.sanji.bomberpro.services.SubscriptionPlan
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch

@Composable
fun PricingScreen(
    service: SettingsService,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()

    var packs by remember { mutableStateOf<List<CreditPack>>(emptyList()) }
    var plans by remember { mutableStateOf<List<SubscriptionPlan>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showAddPack by remember { mutableStateOf(false) }
    var showAddPlan by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        loading = true
        packs = service.getCreditPacks()
        plans = service.getPlans()
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
                "Pricing",
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

        // Credit packs
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💰 Credit Packs", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, modifier = Modifier.weight(1f))
            IconButton(onClick = { showAddPack = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.PlusOne, "Add", tint = colors.primary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        if (packs.isEmpty()) {
            Text("No packs", fontSize = 11.sp, color = colors.onSurfaceVariant)
        } else {
            packs.forEach { p ->
                Surface(shape = RoundedCornerShape(10.dp), color = colors.surface, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                            Text("${p.credits} credits", fontSize = 10.sp, color = colors.onSurfaceVariant)
                        }
                        Text("₹${p.priceInr}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                        IconButton(onClick = {
                            scope.launch {
                                val updated = packs.filter { it.id != p.id }
                                service.saveCreditPacks(updated)
                                refresh()
                            }
                        }) { Icon(Icons.Default.Delete, "Delete", tint = colors.error) }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Plans
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🏆 Subscription Plans", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, modifier = Modifier.weight(1f))
            IconButton(onClick = { showAddPlan = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.PlusOne, "Add", tint = colors.primary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        if (plans.isEmpty()) {
            Text("No plans", fontSize = 11.sp, color = colors.onSurfaceVariant)
        } else {
            plans.forEach { p ->
                Surface(shape = RoundedCornerShape(10.dp), color = colors.surface, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                            Text("${p.durationDays} days · ${p.dailyLimit} SMS/day", fontSize = 10.sp, color = colors.onSurfaceVariant)
                        }
                        Text("₹${p.priceInr}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                        IconButton(onClick = {
                            scope.launch {
                                val updated = plans.filter { it.id != p.id }
                                service.savePlans(updated)
                                refresh()
                            }
                        }) { Icon(Icons.Default.Delete, "Delete", tint = colors.error) }
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

        Spacer(Modifier.height(16.dp))
    }

    if (showAddPack) AddPackDialog(
        onDismiss = { showAddPack = false },
        onSave = { name, credits, price ->
            scope.launch {
                val newPack = CreditPack(
                    id = "p${System.currentTimeMillis()}",
                    name = name,
                    credits = credits,
                    priceInr = price,
                )
                val updated = packs + newPack
                if (service.saveCreditPacks(updated)) {
                    showAddPack = false
                    success = "Pack added"
                    refresh()
                } else error = "Save failed"
            }
        },
    )

    if (showAddPlan) AddPlanDialog(
        onDismiss = { showAddPlan = false },
        onSave = { id, name, price, days, limit ->
            scope.launch {
                val newPlan = SubscriptionPlan(
                    id = id,
                    name = name,
                    priceInr = price,
                    durationDays = days,
                    dailyLimit = limit,
                )
                val updated = plans + newPlan
                if (service.savePlans(updated)) {
                    showAddPlan = false
                    success = "Plan added"
                    refresh()
                } else error = "Save failed"
            }
        },
    )
}

@Composable
private fun AddPackDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, credits: Int, price: Int) -> Unit,
) {
    val colors = LocalSanjiColors.current
    var name by remember { mutableStateOf("") }
    var credits by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }

    val canSubmit = name.isNotBlank() && (credits.toIntOrNull() ?: 0) > 0 && (price.toIntOrNull() ?: 0) > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onSave(name.trim(), credits.toInt(), price.toInt())
            }, enabled = canSubmit) { Text("Add", color = colors.primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colors.onSurfaceVariant) }
        },
        containerColor = colors.surface,
        titleContentColor = colors.onBackground,
        textContentColor = colors.onBackground,
        title = { Text("Add Credit Pack") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = credits,
                    onValueChange = { credits = it.filter { c -> c.isDigit() } },
                    label = { Text("Credits") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it.filter { c -> c.isDigit() } },
                    label = { Text("Price (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun AddPlanDialog(
    onDismiss: () -> Unit,
    onSave: (id: String, name: String, price: Int, days: Int, limit: Int) -> Unit,
) {
    val colors = LocalSanjiColors.current
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("30") }
    var limit by remember { mutableStateOf("5000") }

    val canSubmit = id.isNotBlank() && name.isNotBlank() && (price.toIntOrNull() ?: 0) > 0 &&
            (days.toIntOrNull() ?: 0) > 0 && (limit.toIntOrNull() ?: 0) > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onSave(id.trim().lowercase(), name.trim(), price.toInt(), days.toInt(), limit.toInt())
            }, enabled = canSubmit) { Text("Add", color = colors.primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colors.onSurfaceVariant) }
        },
        containerColor = colors.surface,
        titleContentColor = colors.onBackground,
        textContentColor = colors.onBackground,
        title = { Text("Add Subscription Plan") },
        text = {
            Column {
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it.lowercase() },
                    label = { Text("Plan id (e.g. pro, elite)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it.filter { c -> c.isDigit() } },
                    label = { Text("Price (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = days,
                    onValueChange = { days = it.filter { c -> c.isDigit() } },
                    label = { Text("Duration (days)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = limit,
                    onValueChange = { limit = it.filter { c -> c.isDigit() } },
                    label = { Text("Daily SMS limit") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}