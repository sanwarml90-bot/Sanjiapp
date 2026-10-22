package com.sanji.bomberpro.ui.announcements

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.services.Announcement
import com.sanji.bomberpro.services.AnnouncementService
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FMT = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
private fun fmt(ts: Long): String =
    try { FMT.format(Date(ts * 1000L)) } catch (_: Exception) { "—" }

@Composable
fun AnnouncementsScreen(
    uid: String,
    isAdmin: Boolean,
    adminUid: String,
    adminName: String,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val service = remember { AnnouncementService() }

    var announcements by remember { mutableStateOf<List<Announcement>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showCreate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        loading = true
        try {
            announcements = if (isAdmin) {
                service.listAnnouncements()
            } else {
                service.listUserAnnouncements(uid)
            }
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    LaunchedEffect(error) {
        if (error != null) {
            kotlinx.coroutines.delay(4000)
            error = null
        }
    }
    LaunchedEffect(success) {
        if (success != null) {
            kotlinx.coroutines.delay(4000)
            success = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
                }
                Text(
                    "Announcements",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = colors.onBackground, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { scope.launch { refresh() } }) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = colors.primary)
                }
                if (isAdmin) {
                    IconButton(onClick = { showCreate = true }) {
                        Icon(Icons.Default.Add, "New", tint = colors.primary)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primary)
                }
            } else if (announcements.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📢", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No announcements yet", fontSize = 14.sp, color = colors.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(announcements) { a ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (a.priority == "high") colors.primary.copy(alpha = 0.1f) else colors.surface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (a.priority == "high") "🔴" else "📢",
                                        fontSize = 14.sp,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        a.title,
                                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                        color = colors.onBackground, modifier = Modifier.weight(1f),
                                    )
                                    if (isAdmin) {
                                        IconButton(onClick = {
                                            scope.launch {
                                                if (service.deleteAnnouncement(a.id)) {
                                                    success = "Deleted"
                                                    refresh()
                                                } else error = "Delete failed"
                                            }
                                        }) {
                                            Icon(Icons.Default.Delete, "Delete", tint = colors.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(a.body, fontSize = 12.sp, color = colors.onBackground)
                                Spacer(Modifier.height(6.dp))
                                Row {
                                    Text("🕐 ${fmt(a.createdAt)}", fontSize = 10.sp, color = colors.onSurfaceVariant)
                                    if (isAdmin && a.recipients > 0) {
                                        Spacer(Modifier.width(10.dp))
                                        Text("👥 ${a.recipients}", fontSize = 10.sp, color = colors.onSurfaceVariant)
                                    }
                                    if (isAdmin && a.sentByName.isNotBlank()) {
                                        Spacer(Modifier.width(10.dp))
                                        Text("by ${a.sentByName}", fontSize = 10.sp, color = colors.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
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

    if (showCreate) {
        CreateAnnouncementDialog(
            onDismiss = { showCreate = false },
            onCreate = { title, body, priority ->
                scope.launch {
                    val res = service.createAndBroadcast(
                        title = title,
                        body = body,
                        sentBy = adminUid,
                        sentByName = adminName,
                        priority = priority,
                    )
                    showCreate = false
                    if (res.isSuccess) {
                        val r = res.getOrNull()
                        success = "Sent to ${r?.notificationsWritten ?: 0} users"
                        refresh()
                    } else {
                        error = res.exceptionOrNull()?.message ?: "Failed"
                    }
                }
            },
        )
    }
}

@Composable
private fun CreateAnnouncementDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, body: String, priority: String) -> Unit,
) {
    val colors = LocalSanjiColors.current
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("normal") }

    val canSubmit = title.isNotBlank() && body.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (canSubmit) onCreate(title.trim(), body.trim(), priority) },
                enabled = canSubmit,
            ) { Text("Send to All", color = colors.primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colors.onSurfaceVariant) }
        },
        containerColor = colors.surface,
        titleContentColor = colors.onBackground,
        textContentColor = colors.onBackground,
        title = { Text("New Announcement") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Title", fontSize = 11.sp, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(120) },
                    placeholder = { Text("e.g. New feature released") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(10.dp))
                Text("Message", fontSize = 11.sp, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it.take(2000) },
                    placeholder = { Text("Type announcement…") },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${body.length} / 2000", fontSize = 10.sp, color = colors.onSurfaceVariant)

                Spacer(Modifier.height(10.dp))
                Text("Priority", fontSize = 11.sp, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = { priority = "normal" },
                        shape = RoundedCornerShape(10.dp),
                        color = if (priority == "normal") colors.primary.copy(alpha = 0.15f) else colors.background,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            "Normal",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (priority == "normal") colors.primary else colors.onBackground,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
                    Surface(
                        onClick = { priority = "high" },
                        shape = RoundedCornerShape(10.dp),
                        color = if (priority == "high") colors.error.copy(alpha = 0.15f) else colors.background,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            "High",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (priority == "high") colors.error else colors.onBackground,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
                }
            }
        },
    )
}