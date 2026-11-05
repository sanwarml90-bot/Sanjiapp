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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.data.SpecialMessage
import com.sanji.bomberpro.services.SpecialMsgService
import com.sanji.bomberpro.theme.LocalSanjiColors
import com.sanji.bomberpro.ui.components.SanjiEmoji
import com.sanji.bomberpro.ui.components.pressScale
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FMT = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
private fun fmt(ts: Long): String =
    try { FMT.format(Date(ts * 1000L)) } catch (_: Exception) { "—" }

@Composable
fun SpecialMsgAdminScreen(
    adminUid: String,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val service = remember { SpecialMsgService() }

    var current by remember { mutableStateOf<SpecialMessage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var publishing by remember { mutableStateOf(false) }

    // form state
    var title by remember { mutableStateOf("") }
    var bodyText by remember { mutableStateOf("") }
    var bodyHtml by remember { mutableStateOf("") }
    var isHtml by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(true) }
    var buttonText by remember { mutableStateOf("") }
    var buttonUrl by remember { mutableStateOf("") }

    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    suspend fun refresh() {
        loading = true
        try {
            current = service.getActiveMessage()
            current?.let { c ->
                title = c.title
                bodyText = c.body
                bodyHtml = c.bodyHtml
                isHtml = c.isHtml
                enabled = c.enabled
                buttonText = c.buttonText
                buttonUrl = c.buttonUrl
            }
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    LaunchedEffect(error) {
        if (error != null) { kotlinx.coroutines.delay(4000); error = null }
    }
    LaunchedEffect(success) {
        if (success != null) { kotlinx.coroutines.delay(4000); success = null }
    }

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
                "Special Message",
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = colors.onBackground, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { scope.launch { refresh() } }, enabled = !loading && !publishing) {
                Icon(Icons.Default.Refresh, "Refresh", tint = colors.primary)
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "Publishes a full-page message that appears on next app open for ALL users (once per version).",
            fontSize = 11.sp, color = colors.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        // Current state
        if (loading) {
            Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
            return@Column
        }

        val c = current
        if (c == null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(SanjiEmoji.INFO, fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "No message published yet",
                        fontSize = 12.sp, color = colors.onSurfaceVariant,
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (c.enabled) colors.success.copy(alpha = 0.10f) else colors.error.copy(alpha = 0.10f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (c.enabled) SanjiEmoji.SUCCESS else SanjiEmoji.ERROR,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (c.enabled) "LIVE — v${c.version}" else "DISABLED — v${c.version}",
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = if (c.enabled) colors.success else colors.error,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        c.title.ifBlank { "(no title)" },
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = colors.onBackground,
                    )
                    Text(
                        "Published ${fmt(c.publishedAt)} · by ${c.publishedBy.take(8)}",
                        fontSize = 10.sp, color = colors.onSurfaceVariant,
                    )
                    if (c.enabled) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                onClick = {
                                    scope.launch {
                                        val ok = service.disableMessage(adminUid)
                                        if (ok) { success = "Disabled"; refresh() }
                                        else error = "Failed"
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = colors.warning.copy(alpha = 0.15f),
                                modifier = Modifier.weight(1f).pressScale(),
                            ) {
                                Text(
                                    "Disable",
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    color = colors.warning,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                )
                            }
                            Surface(
                                onClick = { showDeleteConfirm = true },
                                shape = RoundedCornerShape(10.dp),
                                color = colors.error.copy(alpha = 0.15f),
                                modifier = Modifier.weight(1f).pressScale(),
                            ) {
                                Text(
                                    "Delete",
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    color = colors.error,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "COMPOSE MESSAGE",
            fontSize = 11.sp, fontWeight = FontWeight.Bold,
            color = colors.primary, letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(120) },
            label = { Text("Title") },
            placeholder = { Text("e.g. New features released!") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))

        // HTML toggle
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Use HTML",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = colors.onBackground,
                    )
                    Text(
                        "Rich text: bold, links, line breaks",
                        fontSize = 10.sp, color = colors.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = isHtml,
                    onCheckedChange = { isHtml = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.primary,
                        checkedTrackColor = colors.primary.copy(alpha = 0.4f),
                    ),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (isHtml) {
            OutlinedTextField(
                value = bodyHtml,
                onValueChange = { bodyHtml = it.take(8000) },
                label = { Text("HTML body") },
                placeholder = {
                    Text("<b>Important:</b><br/>New features live<br/><a href=\"https://t.me/sanjipro5\">Contact</a>")
                },
                minLines = 6,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${bodyHtml.length} / 8000",
                fontSize = 10.sp, color = colors.onSurfaceVariant,
            )
        } else {
            OutlinedTextField(
                value = bodyText,
                onValueChange = { bodyText = it.take(5000) },
                label = { Text("Message body") },
                placeholder = { Text("Type your message…") },
                minLines = 6,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${bodyText.length} / 5000",
                fontSize = 10.sp, color = colors.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            "OPTIONAL CTA BUTTON",
            fontSize = 11.sp, fontWeight = FontWeight.Bold,
            color = colors.primary, letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = buttonText,
            onValueChange = { buttonText = it.take(40) },
            label = { Text("Button text") },
            placeholder = { Text("e.g. Contact Admin") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = buttonUrl,
            onValueChange = { buttonUrl = it.take(500) },
            label = { Text("Button URL") },
            placeholder = { Text("https://t.me/sanjipro5") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(14.dp))

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Enable after publish",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = colors.onBackground,
                    )
                    Text(
                        "Users will see it on next app open",
                        fontSize = 10.sp, color = colors.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.primary,
                        checkedTrackColor = colors.primary.copy(alpha = 0.4f),
                    ),
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                if (title.isBlank()) { error = "Title required"; return@Button }
                if (isHtml && bodyHtml.isBlank()) { error = "HTML body required"; return@Button }
                if (!isHtml && bodyText.isBlank()) { error = "Body required"; return@Button }
                if (buttonText.isNotBlank() && buttonUrl.isBlank()) {
                    error = "Button URL required when button text set"
                    return@Button
                }

                publishing = true
                error = null
                scope.launch {
                    val res = service.publishMessage(
                        adminUid = adminUid,
                        title = title.trim(),
                        bodyPlain = bodyText.trim(),
                        bodyHtml = bodyHtml.trim(),
                        isHtml = isHtml,
                        enabled = enabled,
                        buttonText = buttonText.trim(),
                        buttonUrl = buttonUrl.trim(),
                    )
                    publishing = false
                    if (res.isSuccess) {
                        success = "Published v${res.getOrNull()}"
                        refresh()
                    } else {
                        error = res.exceptionOrNull()?.message ?: "Failed"
                    }
                }
            },
            enabled = !publishing,
            modifier = Modifier.fillMaxWidth().height(52.dp).pressScale(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = Color.Black,
            ),
        ) {
            if (publishing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black,
                )
                Spacer(Modifier.width(8.dp))
                Text("Publishing…", fontWeight = FontWeight.Bold)
            } else {
                Text(
                    "${SanjiEmoji.MEGAPHONE} ${if (current == null) "Publish" else "Publish New Version"}",
                    fontWeight = FontWeight.Bold, fontSize = 15.sp,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Publishing bumps the version. Users who saw the previous version will see the new one on next open.",
            fontSize = 10.sp, color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = colors.error, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        if (success != null) {
            Spacer(Modifier.height(12.dp))
            Text(success!!, color = colors.success, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        val ok = service.deleteMessage(adminUid)
                        if (ok) { success = "Deleted"; refresh() }
                        else error = "Failed"
                    }
                }) {
                    Text("Delete", color = colors.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = colors.onSurfaceVariant)
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.onBackground,
            textContentColor = colors.onBackground,
            title = { Text("Delete Message?") },
            text = {
                Text(
                    "The message will be removed entirely. Users who haven't seen it won't see it.",
                    fontSize = 12.sp,
                )
            },
        )
    }
}