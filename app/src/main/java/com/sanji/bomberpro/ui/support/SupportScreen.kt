package com.sanji.bomberpro.ui.support

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
import androidx.compose.material.icons.filled.PlusOne
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.services.SupportService
import com.sanji.bomberpro.services.Ticket
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SupportView { LIST, NEW, DETAIL }

private val FMT_SHORT = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
private fun fmtShort(ts: Long): String =
    try { FMT_SHORT.format(Date(ts * 1000L)) } catch (_: Exception) { "—" }

@Composable
fun SupportScreen(
    uid: String,
    userName: String,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val service = remember { SupportService() }

    var view by remember { mutableStateOf(SupportView.LIST) }
    var tickets by remember { mutableStateOf<List<Ticket>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedTicket by remember { mutableStateOf<Ticket?>(null) }
    var newText by remember { mutableStateOf("") }
    var replyText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    suspend fun refresh() {
        loading = true
        tickets = service.listMyTickets(uid)
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    when (view) {
        SupportView.LIST -> Column(
            modifier = Modifier.fillMaxSize().background(colors.background).padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
                }
                Text(
                    "Support",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = colors.onBackground, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { scope.launch { refresh() } }) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = colors.primary)
                }
                IconButton(onClick = { view = SupportView.NEW; newText = ""; error = null }) {
                    Icon(Icons.Default.PlusOne, "New", tint = colors.primary)
                }
            }

            Spacer(Modifier.height(12.dp))

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primary)
                }
            } else if (tickets.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📩", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No tickets yet", fontSize = 13.sp, color = colors.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { view = SupportView.NEW },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.Black),
                        ) { Text("Open Ticket") }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(tickets) { t ->
                        Surface(
                            onClick = {
                                selectedTicket = t
                                replyText = ""
                                view = SupportView.DETAIL
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = colors.surface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (t.status == "open") "🟢" else "🔴",
                                        fontSize = 12.sp,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        t.text.take(60),
                                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                        color = colors.onBackground, maxLines = 1,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Replies: ${t.replies.size} · ${fmtShort(t.ts)}",
                                    fontSize = 10.sp, color = colors.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        SupportView.NEW -> Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { view = SupportView.LIST }) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
                }
                Text(
                    "New Ticket",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = colors.onBackground, modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))

            Text("Describe your issue. Admin will reply here.", fontSize = 11.sp, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = newText,
                onValueChange = { newText = it.take(1000) },
                placeholder = { Text("Type your message…") },
                minLines = 6,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${newText.length} / 1000", fontSize = 10.sp, color = colors.onSurfaceVariant)

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (newText.isBlank()) { error = "Message required"; return@Button }
                    submitting = true
                    error = null
                    scope.launch {
                        val result = service.submitTicket(uid, userName, newText)
                        submitting = false
                        if (result.isSuccess) {
                            newText = ""
                            view = SupportView.LIST
                            refresh()
                        } else {
                            error = result.exceptionOrNull()?.message
                        }
                    }
                },
                enabled = !submitting && newText.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.Black),
            ) {
                if (submitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
                } else {
                    Icon(Icons.Default.Send, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Submit Ticket", fontWeight = FontWeight.Bold)
                }
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error!!, color = colors.error, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        SupportView.DETAIL -> {
            val t = selectedTicket
            if (t == null) {
                view = SupportView.LIST
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.background)
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { view = SupportView.LIST; refresh() }) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
                        }
                        Text(
                            "Ticket",
                            fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            color = colors.onBackground, modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (t.status == "open") "🟢 Open" else "🔴 Closed",
                            fontSize = 11.sp, color = colors.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item {
                            MessageBubble(
                                text = t.text,
                                fromUid = t.uid,
                                isOwn = true,
                                ts = t.ts,
                                isOwner = true,
                                label = "You",
                            )
                        }
                        items(t.replies) { r ->
                            val isFromMe = r.fromUid == uid
                            MessageBubble(
                                text = r.text,
                                fromUid = r.fromUid,
                                isOwn = isFromMe,
                                ts = r.ts,
                                isOwner = false,
                                label = if (isFromMe) "You" else "Admin",
                            )
                        }
                    }

                    if (t.status == "open") {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = replyText,
                                onValueChange = { replyText = it.take(1000) },
                                placeholder = { Text("Type reply…") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (replyText.isBlank()) return@IconButton
                                    scope.launch {
                                        service.addTicketReply(t.id, uid, replyText)
                                        replyText = ""
                                        val fresh = service.findTicket(t.id)
                                        if (fresh != null) selectedTicket = fresh
                                    }
                                },
                                enabled = replyText.isNotBlank(),
                            ) {
                                Icon(Icons.Default.Send, "Send", tint = colors.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    text: String,
    fromUid: String,
    isOwn: Boolean,
    ts: Long,
    isOwner: Boolean,
    label: String,
) {
    val colors = LocalSanjiColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isOwner) colors.primary.copy(alpha = 0.15f) else colors.surface,
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(
                    label,
                    fontSize = 9.sp, color = colors.primary, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(text, fontSize = 12.sp, color = colors.onBackground)
                Spacer(Modifier.height(4.dp))
                Text(fmtShort(ts), fontSize = 9.sp, color = colors.onSurfaceVariant)
            }
        }
    }
}