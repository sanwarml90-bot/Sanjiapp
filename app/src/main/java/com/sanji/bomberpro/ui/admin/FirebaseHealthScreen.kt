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
                            results