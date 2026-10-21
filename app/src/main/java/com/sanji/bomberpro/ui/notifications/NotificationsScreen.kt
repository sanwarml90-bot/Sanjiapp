package com.sanji.bomberpro.ui.notifications

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
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.services.NotificationsService
import com.sanji.bomberpro.services.UserNotification
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FMT_SHORT = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
private fun fmtShort(ts: Long): String =
    try { FMT_SHORT.format(Date(ts * 1000L)) } catch (_: Exception) { "—" }

@Composable
fun NotificationsScreen(
    uid: String,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val service = remember { NotificationsService() }

    var notifications by remember { mutableStateOf<List<UserNotification>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var marking by remember { mutableStateOf(false) }

    suspend fun refresh() {
        loading = true
        notifications = service.listNotifications(uid, 100)
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
            }
            Text(
                "Notifications",
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = colors.onBackground, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { scope.launch { refresh() } }) {
                Icon(Icons.Default.Refresh, "Refresh", tint = colors.primary)
            }
            IconButton(
                onClick = {
                    marking = true
                    scope.launch {
                        service.markAllRead(uid)
                        marking = false
                        refresh()
                    }
                },
                enabled = !marking && notifications.any { !it.read },
            ) {
                Icon(
                    Icons.Default.DoneAll, "Mark all read",
                    tint = if (notifications.any { !it.read }) colors.primary else colors.onSurfaceVariant,
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

        if (notifications.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔔", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("No notifications yet", fontSize = 13.sp, color = colors.onSurfaceVariant)
                }
            }
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(notifications) { n ->
                Surface(
                    onClick = {
                        if (!n.read) {
                            scope.launch {
                                service.markRead(n.id)
                                refresh()
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (n.read) colors.surface else colors.primary.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                emojiForType(n.type),
                                fontSize = 16.sp,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                n.title,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = colors.onBackground, modifier = Modifier.weight(1f),
                            )
                            if (!n.read) {
                                Text("●", fontSize = 14.sp, color = colors.primary)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            n.body,
                            fontSize = 12.sp, color = colors.onBackground,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            fmtShort(n.ts),
                            fontSize = 10.sp, color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun emojiForType(t: String): String = when (t) {
    "broadcast" -> "📢"
    "payment" -> "💳"
    "subscription" -> "🏆"
    "otp" -> "🔐"
    else -> "🔔"
}