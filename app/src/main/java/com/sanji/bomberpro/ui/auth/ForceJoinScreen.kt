package com.sanji.bomberpro.ui.auth

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.data.AuthManager
import com.sanji.bomberpro.data.ForceJoinChannel
import com.sanji.bomberpro.data.ForceJoinState
import com.sanji.bomberpro.services.ForceJoinService
import com.sanji.bomberpro.theme.LocalSanjiColors
import com.sanji.bomberpro.ui.components.SanjiEmoji
import com.sanji.bomberpro.ui.components.fadeSlideIn
import com.sanji.bomberpro.ui.components.pressScale
import kotlinx.coroutines.launch

@Composable
fun ForceJoinScreen(
    authManager: AuthManager,
    uid: String,
    userName: String,
    isAdmin: Boolean = false,
    onVerified: () -> Unit,
    onSkip: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { ForceJoinService() }

    var state by remember { mutableStateOf<ForceJoinState?>(null) }
    var loading by remember { mutableStateOf(true) }
    var verifying by remember { mutableStateOf(false) }
    var verifyError by remember { mutableStateOf<String?>(null) }
    var joinedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        // Owner / admin never blocked by force-join
        if (isAdmin) {
            onVerified()
            return@LaunchedEffect
        }

        loading = true
        val s = service.loadForceJoinState()
        state = s

        if (!s.enabled || s.channels.isEmpty()) {
            loading = false
            onVerified()
            return@LaunchedEffect
        }

        val bypassed = service.isUserBypassed(uid)
        val already = service.isUserVerified(uid)
        if (bypassed || already) {
            loading = false
            onVerified()
            return@LaunchedEffect
        }

        loading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Text(
                "JOIN REQUIRED",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = colors.onBackground,
                letterSpacing = 2.sp,
                modifier = Modifier.fadeSlideIn(delayMs = 0),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Join the channels below to continue.",
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.fadeSlideIn(delayMs = 60),
            )

            Spacer(Modifier.height(20.dp))

            if (loading || state == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.primary)
                }
            } else {
                val all = state!!.channels
                val joinedCount = all.count { it.id in joinedIds }
                val total = all.size

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surface,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).fadeSlideIn(delayMs = 100),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(SanjiEmoji.USERS, fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "$joinedCount / $total channels joined",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        if (joinedCount == total && total > 0) {
                            Text(SanjiEmoji.SUCCESS, fontSize = 14.sp)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(all) { channel ->
                        val isJoined = channel.id in joinedIds
                        ForceJoinCard(
                            channel = channel,
                            isJoined = isJoined,
                            onJoin = {
                                try {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(channel.link),
                                    )
                                    context.startActivity(intent)
                                    joinedIds = joinedIds + channel.id
                                } catch (_: Exception) {
                                    verifyError = "Could not open Telegram"
                                }
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (verifyError != null) {
                Text(
                    verifyError!!,
                    color = colors.error,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    verifying = true
                    verifyError = null
                    scope.launch {
                        val result = service.requestVerification(
                            uid = uid,
                            userName = userName,
                        )
                        verifying = false
                        if (result.isSuccess) {
                            onVerified()
                        } else {
                            verifyError = "Verification failed — try again."
                        }
                    }
                },
                enabled = !verifying && !loading,
                modifier = Modifier.fillMaxWidth().height(52.dp).pressScale(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = Color.Black,
                ),
            ) {
                if (verifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Verifying…", fontWeight = FontWeight.Bold)
                } else {
                    Text(
                        "${SanjiEmoji.SUCCESS} I'VE JOINED ALL",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            TextButton(
                onClick = {
                    authManager.signOut()
                    onSkip()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Skip for now",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ForceJoinCard(
    channel: ForceJoinChannel,
    isJoined: Boolean,
    onJoin: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isJoined) colors.primary.copy(alpha = 0.08f) else colors.surface,
        modifier = Modifier.fillMaxWidth().fadeSlideIn(delayMs = 150),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isJoined) colors.success.copy(alpha = 0.15f) else colors.primary.copy(alpha = 0.12f),
                        RoundedCornerShape(50),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isJoined) Icons.Default.CheckCircle else Icons.Default.OpenInNew,
                    contentDescription = null,
                    tint = if (isJoined) colors.success else colors.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    channel.title.ifBlank { "Channel" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onBackground,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (isJoined) "Tapped — tap Verify below"
                    else "Tap JOIN to open",
                    fontSize = 11.sp,
                    color = if (isJoined) colors.success else colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onJoin,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isJoined) colors.success.copy(alpha = 0.15f) else colors.primary,
                    contentColor = if (isJoined) colors.success else Color.Black,
                ),
                modifier = Modifier.height(40.dp).pressScale(),
            ) {
                if (isJoined) {
                    Text("JOINED", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "JOIN",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}