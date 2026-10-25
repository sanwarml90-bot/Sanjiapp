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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.data.PaymentRequest
import com.sanji.bomberpro.services.AdminService
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FMT = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
private fun fmt(ts: Long): String =
    try { FMT.format(Date(ts * 1000L)) } catch (_: Exception) { "—" }

@Composable
fun PaymentReviewScreen(
    adminUid: String,
    service: AdminService,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()

    var payments by remember { mutableStateOf<List<PaymentRequest>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var actionRunning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    var showApprove by remember { mutableStateOf<PaymentRequest?>(null) }
    var showReject by remember { mutableStateOf<PaymentRequest?>(null) }
    var rejectReason by remember { mutableStateOf("") }

    suspend fun refresh() {
        loading = true
        try {
            payments = service.listPendingPayments()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    LaunchedEffect(error) {
        if (error != null) { kotlinx.coroutines.delay(4000); error = null }
    }
    LaunchedEffect(success) {
        if (success != null) { kotlinx.coroutines.delay(3000); success = null }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
                }
                Text(
                    "Payment Review (${payments.size})",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = colors.onBackground, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { scope.launch { refresh() } }) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = colors.primary)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Pending UPI payment requests. Approve credits the user directly. No Telegram bot needed.",
                fontSize = 11.sp, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primary)
                }
            } else if (payments.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✅", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No pending payments", fontSize = 14.sp, color = colors.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "New payment submissions will appear here",
                            fontSize = 11.sp, color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(payments) { p ->
                        PaymentCard(
                            p = p,
                            onApprove = { showApprove = p },
                            onReject = {
                                rejectReason = ""
                                showReject = p
                            },
                        )
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
    }

    val toApprove = showApprove
    if (toApprove != null) {
        AlertDialog(
            onDismissRequest = { if (!actionRunning) showApprove = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        actionRunning = true
                        scope.launch {
                            val res = service.approvePayment(toApprove.id, adminUid)
                            actionRunning = false
                            showApprove = null
                            if (res.isSuccess) {
                                success = "Approved · ₹${toApprove.amount}"
                                refresh()
                            } else {
                                error = res.exceptionOrNull()?.message ?: "Approve failed"
                            }
                        }
                    },
                    enabled = !actionRunning,
                ) { Text("APPROVE", color = colors.success, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { if (!actionRunning) showApprove = null }) {
                    Text("Cancel", color = colors.onSurfaceVariant)
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.onBackground,
            textContentColor = colors.onBackground,
            title = { Text("Approve Payment?") },
            text = {
                Column {
                    Text(
                        "Amount: ₹${toApprove.amount}",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onBackground,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Item: ${if (toApprove.type == "subscription") toApprove.plan.uppercase() else "${toApprove.packName} (${toApprove.creditsAmount} credits)"}",
                        fontSize = 12.sp, color = colors.onBackground,
                    )
                    Text("UTR: ${toApprove.utr}", fontSize = 11.sp, color = colors.primary, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Verifying UTR against your bank statement is YOUR responsibility. This grants credits/subscription immediately.",
                        fontSize = 11.sp, color = colors.warning,
                    )
                }
            },
        )
    }

    val toReject = showReject
    if (toReject != null) {
        AlertDialog(
            onDismissRequest = { if (!actionRunning) showReject = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        actionRunning = true
                        scope.launch {
                            val res = service.rejectPayment(toReject.id, adminUid, rejectReason.trim())
                            actionRunning = false
                            showReject = null
                            if (res.isSuccess) {
                                success = "Rejected"
                                refresh()
                            } else {
                                error = res.exceptionOrNull()?.message ?: "Reject failed"
                            }
                        }
                    },
                    enabled = !actionRunning,
                ) { Text("REJECT", color = colors.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { if (!actionRunning) showReject = null }) {
                    Text("Cancel", color = colors.onSurfaceVariant)
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.onBackground,
            textContentColor = colors.onBackground,
            title = { Text("Reject Payment?") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "User will be notified. Optional reason:",
                        fontSize = 11.sp, color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { rejectReason = it.take(200) },
                        placeholder = { Text("e.g. UTR not found in bank statement") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }
}

@Composable
private fun PaymentCard(
    p: PaymentRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⏳", fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "₹${p.amount} · ${if (p.type == "subscription") "subscription" else "credits"}",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onBackground,
                    )
                    Text(p.userName.ifBlank { "User" }, fontSize = 11.sp, color = colors.onSurfaceVariant, maxLines = 1)
                    Text(
                        p.uid,
                        fontSize = 10.sp, color = colors.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace, maxLines = 1,
                    )
                }
                Text(fmt(p.createdAt), fontSize = 10.sp, color = colors.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))

            Surface(shape = RoundedCornerShape(10.dp), color = colors.background, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Row {
                        Text("Type: ", fontSize = 11.sp, color = colors.onSurfaceVariant)
                        Text(
                            if (p.type == "subscription") "📦 ${p.plan.uppercase()}"
                            else "💰 ${p.creditsAmount} credits",
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground,
                        )
                    }
                    if (p.packName.isNotBlank()) {
                        Text(
                            "Pack: ${p.packName}",
                            fontSize = 11.sp, color = colors.onBackground,
                        )
                    }
                    Text(
                        "UTR: ${p.utr}",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = colors.primary, fontFamily = FontFamily.Monospace,
                    )
                    if (p.description.isNotBlank()) {
                        Text(
                            "Note: ${p.description.take(120)}",
                            fontSize = 10.sp, color = colors.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.success,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Approve", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Button(
                    onClick = onReject,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.error,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reject", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}