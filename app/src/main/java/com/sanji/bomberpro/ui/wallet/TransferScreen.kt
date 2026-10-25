package com.sanji.bomberpro.ui.wallet

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
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
import com.sanji.bomberpro.services.TransferRecipient
import com.sanji.bomberpro.services.TransferResult
import com.sanji.bomberpro.services.TransferService
import com.sanji.bomberpro.services.UserDataService
import com.sanji.bomberpro.services.UserDocObserver
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch

private enum class TransferView { FORM, SUCCESS }
private enum class TransferCurrency { CREDITS, COINS }

@Composable
fun TransferScreen(
    uid: String,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val service = remember { TransferService() }
    val userData = remember { UserDataService() }

    var credits by remember { mutableStateOf(0) }
    var coins by remember { mutableStateOf(0) }

    LaunchedEffect(uid) {
        UserDocObserver.observeUserDoc(uid).collect { map ->
            credits = (map["credits"] as? Number)?.toInt() ?: 0
            coins = (map["coins"] as? Number)?.toInt() ?: 0
        }
    }

    var view by remember { mutableStateOf(TransferView.FORM) }
    var currency by remember { mutableStateOf(TransferCurrency.CREDITS) }
    var query by remember { mutableStateOf("") }
    var recipient by remember { mutableStateOf<TransferRecipient?>(null) }
    var amountText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var successData by remember { mutableStateOf<TransferResult.Success?>(null) }

    Box(
        modifier = Modifier.fillMaxSize().background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (view == TransferView.SUCCESS) {
                        view = TransferView.FORM
                        query = ""
                        recipient = null
                        amountText = ""
                        successData = null
                    } else {
                        onBack()
                    }
                }) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
                }
                Text(
                    "Transfer",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = colors.onBackground, modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))

            when (view) {
                TransferView.FORM -> Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "Send credits or coins to another user",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CurrencyChip(
                            label = "💰 Credits",
                            subLabel = "$credits",
                            selected = currency == TransferCurrency.CREDITS,
                            onClick = {
                                currency = TransferCurrency.CREDITS
                                recipient = null
                                amountText = ""
                                error = null
                            },
                            modifier = Modifier.weight(1f),
                        )
                        CurrencyChip(
                            label = "🪙 Coins",
                            subLabel = "$coins",
                            selected = currency == TransferCurrency.COINS,
                            onClick = {
                                currency = TransferCurrency.COINS
                                recipient = null
                                amountText = ""
                                error = null
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    if (recipient == null) {
                        Text(
                            "Enter recipient username, UID, email, or refer code",
                            fontSize = 11.sp,
                            color = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it.trim() },
                            placeholder = { Text("username · UID · email · SJXXXXXX") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (query.isBlank()) {
                                    error = "Enter a recipient"
                                    return@Button
                                }
                                loading = true
                                error = null
                                scope.launch {
                                    val found = service.findRecipient(query)
                                    loading = false
                                    if (found == null) {
                                        error = "Recipient not found"
                                    } else if (found.uid == uid) {
                                        error = "Cannot send to yourself"
                                    } else {
                                        recipient = found
                                    }
                                }
                            },
                            enabled = !loading && query.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primary,
                                contentColor = Color.Black,
                            ),
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.Black,
                                )
                            } else {
                                Text("Find recipient", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        val r = recipient!!
                        val available = if (currency == TransferCurrency.CREDITS) credits else coins
                        val maxAllowed = if (currency == TransferCurrency.CREDITS)
                            service.maxTransferable(available) else available

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = colors.surface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("To", fontSize = 10.sp, color = colors.onSurfaceVariant)
                                    Text(
                                        r.displayName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onBackground,
                                    )
                                    Text(
                                        r.email.ifBlank { r.uid },
                                        fontSize = 11.sp,
                                        color = colors.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = {
                                    recipient = null
                                    amountText = ""
                                }) {
                                    Text("Change", color = colors.primary, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            "Available: $available ${if (currency == TransferCurrency.CREDITS) "credits" else "coins"}",
                            fontSize = 11.sp,
                            color = colors.onSurfaceVariant,
                        )
                        Text(
                            "Max you can send: $maxAllowed",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary,
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                            placeholder = { Text("Amount") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            QuickAmount("10%", (maxAllowed * 10 / 100).coerceAtLeast(1)) { amountText = it.toString() }
                            QuickAmount("25%", (maxAllowed * 25 / 100).coerceAtLeast(1)) { amountText = it.toString() }
                            QuickAmount("50%", (maxAllowed * 50 / 100).coerceAtLeast(1)) { amountText = it.toString() }
                            QuickAmount("Max", maxAllowed) { amountText = it.toString() }
                        }

                        Spacer(Modifier.height(20.dp))

                        Button(
                            onClick = {
                                val amt = amountText.toIntOrNull() ?: 0
                                if (amt <= 0) {
                                    error = "Enter amount"
                                    return@Button
                                }
                                if (amt > maxAllowed) {
                                    error = "Max $maxAllowed"
                                    return@Button
                                }
                                loading = true
                                error = null
                                scope.launch {
                                    val result = when (currency) {
                                        TransferCurrency.CREDITS -> service.transferCredits(uid, r.uid, amt)
                                        TransferCurrency.COINS -> service.transferCoins(uid, r.uid, amt)
                                    }
                                    loading = false
                                    when (result) {
                                        is TransferResult.Success -> {
                                            successData = result
                                            view = TransferView.SUCCESS
                                        }
                                        is TransferResult.Failure -> error = result.message
                                    }
                                }
                            },
                            enabled = !loading && amountText.toIntOrNull().let { it != null && it > 0 },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primary,
                                contentColor = Color.Black,
                            ),
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.Black,
                                )
                            } else {
                                Text("Send", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }

                    if (error != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            error!!,
                            color = colors.error,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                }

                TransferView.SUCCESS -> {
                    val s = successData
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            tint = colors.success,
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Transfer Sent!",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onBackground,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "To ${s?.recipientName ?: "recipient"}",
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(20.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = colors.surface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    "New balance: ${s?.newOwnBalance ?: 0} ${if (currency == TransferCurrency.CREDITS) "credits" else "coins"}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onBackground,
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = {
                                view = TransferView.FORM
                                query = ""
                                recipient = null
                                amountText = ""
                                successData = null
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primary,
                                contentColor = Color.Black,
                            ),
                        ) {
                            Text("Send Another", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onBack) {
                            Text("Back to Wallet", color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrencyChip(
    label: String,
    subLabel: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSanjiColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) colors.primary.copy(alpha = 0.15f) else colors.surface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (selected) colors.primary else colors.onBackground,
            )
            Text(
                subLabel,
                fontSize = 11.sp,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickAmount(label: String, amount: Int, onSet: (Int) -> Unit) {
    val colors = LocalSanjiColors.current
    Surface(
        onClick = { onSet(amount) },
        shape = RoundedCornerShape(8.dp),
        color = colors.primary.copy(alpha = 0.12f),
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.primary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}