package com.sanji.bomberpro.ui.appeal

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
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
import com.sanji.bomberpro.services.Appeal
import com.sanji.bomberpro.services.AppealService
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FMT = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
private fun fmt(ts: Long): String =
    try { FMT.format(Date(ts * 1000L)) } catch (_: Exception) { "—" }

@Composable
fun AppealScreen(
    uid: String,
    userName: String,
    userEmail: String,
    banReason: String,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val service = remember { AppealService() }

    var appeals by remember { mutableStateOf<List<Appeal>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var text by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        loading = true
        try {
            appeals = service.listUserAppeals(uid)
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

    val hasOpen = appeals.any { it.status == "open" }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
                }
                Text(
                    "Appeal Ban",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = colors.onBackground, modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))

            if (banReason.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.error.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Your ban reason:",
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.error,
                        )
                        Text(
                            banReason,
                            fontSize = 12.sp, color = colors.onBackground,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (loading) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primary)
                }
            } else {
                // previous appeals
                if (appeals.isNotEmpty()) {
                    Text("Your appeals", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                    Spacer(Modifier.height(8.dp))
                    appeals.forEach { a ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.surface,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        when (a.status) {
                                            "resolved" -> Icons.Default.CheckCircle
                                            "rejected" -> Icons.Default.Error
                                            else -> Icons.Default.HourglassEmpty
                                        },
                                        null,
                                        tint = when (a.status) {
                                            "resolved" -> colors.success
                                            "rejected" -> colors.error
                                            else -> colors.warning
                                        },
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        a.status.replaceFirstChar { it.uppercase() },
                                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                        color = colors.onBackground, modifier = Modifier.weight(1f),
                                    )
                                    Text(fmt(a.createdAt), fontSize = 10.sp, color = colors.onSurfaceVariant)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(a.text, fontSize = 11.sp, color = colors.onBackground, maxLines = 4)
                                if (a.adminReply.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Surface(shape = RoundedCornerShape(8.dp), color = colors.background) {
                                        Column(Modifier.padding(8.dp)) {
                                            Text("Admin reply:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                                            Text(a.adminReply, fontSize = 11.sp, color = colors.onBackground)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                if (!hasOpen) {
                    Text("Why should you be unbanned?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(1000) },
                        placeholder = { Text("Explain your situation…") },
                        minLines = 5,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${text.length} / 1000", fontSize = 10.sp, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (text.isBlank()) { error = "Please write your appeal"; return@Button }
                            submitting = true
                            error = null
                            scope.launch {
                                val res = service.submitAppeal(
                                    uid = uid,
                                    userName = userName,
                                    userEmail = userEmail,
                                    banReason = banReason,
                                    text = text,
                                )
                                submitting = false
                                if (res.isSuccess) {
                                    success = "Appeal submitted"
                                    text = ""
                                    refresh()
                                } else {
                                    error = res.exceptionOrNull()?.message ?: "Failed"
                                }
                            }
                        },
                        enabled = !submitting && text.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary, contentColor = Color.Black,
                        ),
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.Black)
                        } else {
                            Text("Submit Appeal", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.warning.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "You already have an open appeal",
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.warning,
                            )
                            Text(
                                "Wait for admin reply. You can submit a new appeal once this one is resolved.",
                                fontSize = 11.sp, color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
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
}