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
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
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
import com.sanji.bomberpro.services.GiftResult
import com.sanji.bomberpro.services.GiftService
import com.sanji.bomberpro.services.TransferRecipient
import com.sanji.bomberpro.services.UserDocObserver
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch

private enum class GiftView { FORM, SUCCESS }

@Composable
fun GiftScreen(
    uid: String,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val service = remember { GiftService() }

    var coins by remember { mutableStateOf(0) }
    LaunchedEffect(uid) {
        UserDocObserver.observeUserDoc(uid).collect { map ->
            coins = (map["coins"] as? Number)?.toInt() ?: 0
        }
    }

    var view by remember { mutableStateOf(GiftView.FORM) }
    var query by remember { mutableStateOf("") }
    var recipient by remember { mutableStateOf<TransferRecipient?>(null) }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var successData by remember { mutableStateOf<GiftResult.Success?>(null) }

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
                    if (view == GiftView.SUCCESS) {
                        view = GiftView.FORM
                        query = ""
                        recipient = null
                        amountText = ""
                        note = ""
                        successData = null
                    } else {
                        onBack()
                    }
                }) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
                }
                Text(
                    "Gift Coins",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = colors.onBackground, modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))

            when (view) {
                GiftView.FORM -> Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "Send coins as a one-way gift to a friend",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "🪙 Your coins: $coins",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primary,
                    )

                    Spacer(Modifier.height(20.dp))

                    if (recipient == null) {
                        Text(
                            "Enter recipient UID, email, or refer code",
                            fontSize = 11.sp,
                            color = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it.trim() },
                            placeholder = { Text("UID or email or SJXXXXXX") },
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
                                        error = "Cannot gift to yourself"
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

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = colors.surface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CardGiftcard, null, tint = colors.primary, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(10.dp))
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

                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                            placeholder = { Text("Coins amount (max $coins)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it.take(60) },
                            placeholder = { Text("Optional note (max 60)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(20.dp))

                        Button(
                            onClick = {
                                val amt = amountText.toIntOrNull() ?: 0
                                if (amt <= 0) { error = "Enter amount"; return@Button }
                                if (amt > coins) { error = "Not enough coins"; return@Button }
                                loading = true
                                error = null
                                scope.launch {
                                    val result = service.giftCoins(uid, r.uid, amt, note)
                                    loading = false
                                    when (result) {
                                        is GiftResult.Success -> {
                                            successData = result
                                            view = GiftView.SUCCESS
                                        }
                                        is GiftResult.Failure -> error = result.message
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
                                Text("Send Gift 🎁", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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

                GiftView.SUCCESS -> {
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
                            "Gift Sent! 🎁",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onBackground,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${s?.amount ?: 0} coins to ${s?.recipientName ?: "friend"}",
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(20.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = colors.surface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    "Your new balance: ${s?.newOwnBalance ?: 0} coins",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onBackground,
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = {
                                view = GiftView.FORM
                                query = ""
                                recipient = null
                                amountText = ""
                                note = ""
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