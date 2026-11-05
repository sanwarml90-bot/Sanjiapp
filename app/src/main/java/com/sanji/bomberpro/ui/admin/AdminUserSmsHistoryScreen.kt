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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.data.BlastRecord
import com.sanji.bomberpro.services.UserDataService
import com.sanji.bomberpro.theme.LocalSanjiColors
import com.sanji.bomberpro.ui.components.SanjiEmoji
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FMT = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
private fun fmt(ts: Long): String =
    try { FMT.format(Date(ts * 1000L)) } catch (_: Exception) { "—" }

/**
 * SMS history viewer.
 * Works for both live users AND soft-deleted accounts (history survives with uid).
 */
@Composable
fun AdminUserSmsHistoryScreen(
    uid: String,
    displayName: String,
    onBack: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val userData = remember { UserDataService() }

    var blasts by remember { mutableStateOf<List<BlastRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    suspend fun refresh() {
        loading = true
        try {
            blasts = userData.getBlastRecords(uid, 100)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(uid) { refresh() }

    Column(modifier = Modifier.fillMaxSize().background(colors.background).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "SMS History",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground,
                )
                Text(
                    displayName.ifBlank { uid }.take(40),
                    fontSize = 11.sp, color = colors.onSurfaceVariant,
                )
                Text(
                    "UID: $uid",
                    fontSize = 9.sp, color = colors.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
            IconButton(onClick = { scope.launch { refresh() } }) {
                Icon(Icons.Default.Refresh, "Refresh", tint = colors.primary)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
            return
        }

        if (blasts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(SanjiEmoji.HISTORY, fontSize = 42.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("No SMS sent by this user", fontSize = 13.sp, color = colors.onSurfaceVariant)
                }
            }
            return
        }

        Text(
            "Total: ${blasts.size} blast(s) shown (max 100)",
            fontSize = 11.sp, color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(blasts) { b ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${SanjiEmoji.PHONE} ${b.targetFull.ifBlank { b.targetMasked }}",
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = colors.primary,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                b.speed.ifBlank { "—" },
                                fontSize = 10.sp, color = colors.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${SanjiEmoji.MESSAGE} ${b.message}",
                            fontSize = 12.sp, color = colors.onBackground,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row {
                            Text("${SanjiEmoji.SUCCESS} ${b.sent}", fontSize = 11.sp, color = colors.success, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(10.dp))
                            Text("${SanjiEmoji.ERROR} ${b.failed}", fontSize = 11.sp, color = colors.error, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(10.dp))
                            Text("${SanjiEmoji.TOTAL} req ${b.requested}", fontSize = 11.sp, color = colors.onSurfaceVariant)
                            Spacer(Modifier.width(10.dp))
                            Text("⏱ ${b.durationSec}s", fontSize = 11.sp, color = colors.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${SanjiEmoji.CLOCK} ${fmt(b.ts)} · status ${b.status}",
                            fontSize = 10.sp, color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}