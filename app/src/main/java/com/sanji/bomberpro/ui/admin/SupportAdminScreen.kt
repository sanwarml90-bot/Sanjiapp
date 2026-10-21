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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import com.sanji.bomberpro.services.Appeal
import com.sanji.bomberpro.services.SupportService
import com.sanji.bomberpro.services.Ticket
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SupportAdminView { TICKETS, DETAIL, APPEALS }

private val FMT_SHORT = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
private fun fmtShort(ts: Long): String =
    try { FMT_SHORT.format(Date(ts * 1000L)) } catch (_: Exception) { "—" }

@Composable
fun SupportAdminScreen(
    adminUid: String,
    adminName: String,
    service: SupportService,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()

    var view by remember { mutableStateOf(SupportAdminView.TICKETS) }
    var tab by remember { mutableStateOf(0) }

    var tickets by remember { mutableStateOf<List<Ticket>>(emptyList()) }
    var appeals by remember { mutableStateOf<List<Appeal>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedTicket by remember { mutableStateOf<Ticket?>(null) }
    var replyText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        loading = true
        tickets = service.listOpenTickets()
        appeals = service.listAppeals(onlyOpen = true)
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    when (view) {
        SupportAdminView.TICKETS -> Column(
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
            }

            Spacer(Modifier.height(8.dp))

            ScrollableTabRow(
                selectedTabIndex = tab,
                containerColor = Color.Transparent,
                contentColor = colors.primary,
                edgePadding = 0.dp,
            ) {
                listOf("Tickets (${tickets.size})", "Appeals (${appeals.size})").forEachIndexed { i, label ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = {
                            Text(label, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                        },
                        selectedContentColor = colors.primary,
                        unselectedContentColor = colors.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primary)
                }
                return
            }

            if (tab == 0) {
                if (tickets.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No open tickets", color = colors.onSurfaceVariant, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(tickets) { t ->
                            Surface(
                                onClick = { selectedTicket = t; replyText = ""; view = SupportAdminView.DETAIL },
                                shape = RoundedCornerShape(12.dp),
                                color = colors.surface,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🟢", fontSize = 12.sp)
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            t.userName.ifBlank { "User" },
                                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                            color = colors.onBackground, modifier = Modifier.weight(1f),
                                        )
                                        Text(fmtShort(t.ts), fontSize = 10.sp, color = colors.onSurfaceVariant)
                                    }
                                    Text(
                                        "UID: ${t.uid}",
                                        fontSize = 9.sp, color = colors.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(t.text.take(80), fontSize = 11.sp, color = colors.onBackground, maxLines = 2)
                                    Text("Replies: ${t.replies.size}", fontSize = 10.sp, color = colors.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            } else {
                if (appeals.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No open appeals", color = colors.onSurfaceVariant, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(appeals) { a ->
                            Surface(shape = RoundedCornerShape(12.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🔓", fontSize = 12.sp)
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            a.userName.ifBlank { "User" },
                                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                            color = colors.onBackground, modifier = Modifier.weight(1f),
                                        )
                                        Text(fmtShort(a.ts), fontSize = 10.sp, color = colors.onSurfaceVariant)
                                    }
                                    Text("UID: ${a.uid}", fontSize = 9.sp, color = colors.onSurfaceVariant)
                                    Spacer(Modifier.height(4.dp))
                                    Text(a.text.take(120), fontSize = 11.sp, color = colors.onBackground, maxLines = 3)
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    service.resolveAppeal(a.id)
                                                    refresh()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = colors.success.copy(alpha = 0.15f),
                                                contentColor = colors.success,
                                            ),
                                            modifier = Modifier.height(32.dp),
                                            shape = RoundedCornerShape(8.dp),
                                        ) {
                                            Icon(Icons.Default.Done, null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Resolved", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        SupportAdminView.DETAIL -> {
            val t = selectedTicket
            if (t == null) view = SupportAdminView.TICKETS
            else Column(
                modifier = Modifier.fillMaxSize().background(colors.background).padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { view = SupportAdminView.TICKETS; refresh() }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
                    }
                    Text(
                        "Ticket",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        color = colors.onBackground, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        scope.launch {
                            service.closeTicket(t.id)
                            view = SupportAdminView.TICKETS
                            refresh()
                        }
                    }) {
                        Icon(Icons.Default.Close, "Close", tint = colors.error)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "From: ${t.userName} (${t.uid})",
                    fontSize = 11.sp, color = colors.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        MessageBubble(
                            text = t.text,
                            isAdmin = false,
                            label = t.userName.ifBlank { "User" },
                            ts = t.ts,
                        )
                    }
                    items(t.replies) { r ->
                        val isAdminReply = r.fromUid == adminUid || r.fromUid != t.uid
                        MessageBubble(
                            text = r.text,
                            isAdmin = isAdminReply,
                            label = if (isAdminReply) "Admin" else t.userName,
                            ts = r.ts,
                        )
                    }
                }

                if (t.status == "open") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it.take(1000) },
                            placeholder = { Text("Reply…") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (replyText.isBlank()) return@IconButton
                                scope.launch {
                                    service.addTicketReply(t.id, adminUid, replyText)
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
                } else {
                    Text(
                        "Ticket closed",
                        fontSize = 12.sp, color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        SupportAdminView.APPEALS -> view = SupportAdminView.TICKETS
    }
}

@Composable
private fun MessageBubble(
    text: String,
    isAdmin: Boolean,
    label: String,
    ts: Long,
) {
    val colors = LocalSanjiColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isAdmin) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isAdmin) colors.primary.copy(alpha = 0.15f) else colors.surface,
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(label, fontSize = 9.sp, color = colors.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(text, fontSize = 12.sp, color = colors.onBackground)
                Spacer(Modifier.height(4.dp))
                Text(fmtShort(ts), fontSize = 9.sp, color = colors.onSurfaceVariant)
            }
        }
    }
}