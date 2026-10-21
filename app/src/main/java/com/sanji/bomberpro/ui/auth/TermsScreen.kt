package com.sanji.bomberpro.ui.auth

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.sanji.bomberpro.data.AuthManager
import com.sanji.bomberpro.services.TermsResult
import com.sanji.bomberpro.services.TermsService
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.launch

@Composable
fun TermsScreen(
    authManager: AuthManager,
    onAccepted: () -> Unit,
    onDeclined: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val scope = rememberCoroutineScope()
    val service = remember { TermsService() }

    var terms by remember { mutableStateOf<TermsResult?>(null) }
    var loading by remember { mutableStateOf(true) }
    var accepting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        terms = service.loadTerms()
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "TERMS & CONDITIONS",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = colors.onBackground,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(4.dp))
            val versionLabel = terms?.version?.let { "v$it" } ?: ""
            Text(
                versionLabel,
                fontSize = 11.sp,
                color = colors.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surface,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                if (loading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colors.primary)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        Text(
                            text = terms?.text ?: TermsService.DEFAULT_TERMS_TEXT,
                            fontSize = 12.sp,
                            color = colors.onBackground,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (error != null) {
                Text(
                    error!!,
                    color = colors.error,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }

            val canAccept = !loading && !accepting && terms != null

            Button(
                onClick = {
                    val t = terms ?: return@Button
                    accepting = true
                    error = null
                    scope.launch {
                        val ok = authManager.acceptTerms(t.version)
                        accepting = false
                        if (ok) {
                            onAccepted()
                        } else {
                            error = "Could not save acceptance. Try again."
                        }
                    }
                },
                enabled = canAccept,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = Color.Black,
                ),
            ) {
                if (accepting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("I AGREE", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Spacer(Modifier.height(6.dp))

            TextButton(
                onClick = {
                    authManager.signOut()
                    onDeclined()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Decline & sign out",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
