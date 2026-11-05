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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.sanji.bomberpro.data.FirebaseNode
import com.sanji.bomberpro.services.AdminService
import com.sanji.bomberpro.services.FirebaseTestResult
import com.sanji.bomberpro.services.RealtimeDbService
import com.sanji.bomberpro.theme.LocalSanjiColors
import com.sanji.bomberpro.ui.components.SanjiEmoji
import com.sanji.bomberpro.ui.components.pressScale
import kotlinx.coroutines.launch

@Composable
fun FirebaseHealthScreen(
    adminUid: String,
    onBack: () -> Unit,
    onFirebasesDeleted: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val rtdb = remember { RealtimeDbService() }
    val adminService = remember { AdminService() }

    var firebases by remember { mutableStateOf<List<FirebaseNode>>(emptyList()) }
    var results by remember { mutableStateOf<List<FirebaseTestResult>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var testing by remember { mutableStateOf(false) }
    var killing by remember { mutableStateOf(false) }
    var tested by remember { mutableStateOf(0) }
    var totalToTest by remember { mutableStateOf(0) }
    var showKillConfirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    suspend fun loadList() {
        loading = true
        firebases = adminService.listFirebases()
        loading = false
    }

    LaunchedEffect(Unit) { loadList() }

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

    val deadIds = results.filter { it.onlineClients == 0 || it.error != null }.map { it.firebaseId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = colors.onBackground)
            }
            Text(
                "Firebase Health",
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = colors.onBackground, modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { scope.launch { loadList() } },
                enabled = !testing && !killing,
            ) {
                Icon(Icons.Default.Refresh, "Refresh", tint = colors.primary)
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "Test all firebases and delete dead ones (0 online or error).",
            fontSize = 12.sp, color = colors.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    testing = true
                    tested = 0
                    totalToTest = firebases.size
                    results = emptyList()
                    error = null
                    success = null
                    scope.launch {
                        try {
                            val found = rtdb.testAllFirebases(firebases) { idx, total, _ ->
                                tested = idx
                                totalToTest = total
                            }
                            results = found
                        } catch (e: Exception) {
                            error = "Test failed: ${e.message}"
                        } finally {
                            testing = false
                        }
                    }
                },
                enabled = !testing && !killing && firebases.isNotEmpty(),
                modifier = Modifier.weight(1f).height(46.dp).pressScale(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = Color.Black,
                ),
            ) {
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.Black,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Testing…", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("${SanjiEmoji.SEARCH} Test All", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = { showKillConfirm = true },
                enabled = !testing && !killing && deadIds.isNotEmpty(),
                modifier = Modifier.weight(1f).height(46.dp).pressScale(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.error,
                    contentColor = Color.White,
                    disabledContainerColor = colors.surface,
                    disabledContentColor = colors.onSurfaceVariant,
                ),
            ) {
                Text(
                    "${SanjiEmoji.DELETE} Kill Dead (${deadIds.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (testing && totalToTest > 0) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { tested.toFloat() / totalToTest.coerceAtLeast(1) },
                color = colors.primary,
                trackColor = colors.surface,
                modifier = Modifier.fillMaxWidth().height(6.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "$tested / $totalToTest",
                fontSize = 11.sp,
                color = colors.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
            return
        }

        if (firebases.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(SanjiEmoji.FIREBASE, fontSize = 42.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("No firebases added yet", fontSize = 13.sp, color = colors.onSurfaceVariant)
                }
            }
            return
        }

        if (results.isEmpty() && !testing) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(SanjiEmoji.NETWORK, fontSize = 42.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap Test All to check ${firebases.size} firebases",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results) { r ->
                ResultCard(result = r, isDead = r.firebaseId in deadIds)
            }
        }

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error!!, color = colors.error, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        if (success != null) {
            Spacer(Modifier.height(10.dp))
            Text(success!!, color = colors.success, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }

    if (showKillConfirm) {
        AlertDialog(
            onDismissRequest = { if (!killing) showKillConfirm = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        killing = true
                        showKillConfirm = false
                        scope.launch {
                            try {
                                val deleted = adminService.deleteFirebases(deadIds, adminUid)
                                success = "Deleted $deleted dead firebase(s)"
                                rtdb.invalidateScanCache()
                                loadList()
                                results = emptyList()
                                onFirebasesDeleted()
                            } catch (e: Exception) {
                                error = "Delete failed: ${e.message}"
                            } finally {
                                killing = false
                            }
                        }
                    },
                    enabled = !killing,
                ) {
                    Text("Kill ${deadIds.size}", color = colors.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showKillConfirm = false }, enabled = !killing) {
                    Text("Cancel", color = colors.onSurfaceVariant)
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.onBackground,
            textContentColor = colors.onBackground,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(SanjiEmoji.WARNING, fontSize = 18.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("Kill Dead Firebases?")
                }
            },
            text = {
                Column {
                    Text(
                        "This will permanently delete ${deadIds.size} firebase(s) — those with 0 online clients or any error.",
                        fontSize = 12.sp,
                        color = colors.onBackground,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Action cannot be undone. Online devices on surviving firebases are unaffected.",
                        fontSize = 11.sp,
                        color = colors.error,
                    )
                }
            },
        )
    }
}

@Composable
private fun ResultCard(result: FirebaseTestResult, isDead: Boolean) {
    val colors = LocalSanjiColors.current
    val isHealthy = result.error == null && result.onlineClients > 0

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isHealthy) SanjiEmoji.SUCCESS else SanjiEmoji.ERROR,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    result.firebaseLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground,
                    modifier = Modifier.weight(1f),
                )
                if (isDead) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.error.copy(alpha = 0.15f),
                    ) {
                        Text(
                            "DEAD",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.error,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                result.firebaseUrl.take(60),
                fontSize = 10.sp,
                color = colors.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${SanjiEmoji.USERS} ${result.totalClients} total",
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    "${SanjiEmoji.NETWORK} ${result.onlineClients} online",
                    fontSize = 11.sp,
                    color = if (result.onlineClients > 0) colors.success else colors.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (result.error != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    result.error,
                    fontSize = 11.sp,
                    color = colors.error,
                )
            }
        }
    }
}