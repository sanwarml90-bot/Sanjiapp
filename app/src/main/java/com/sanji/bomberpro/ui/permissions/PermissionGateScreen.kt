package com.sanji.bomberpro.ui.permissions

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.theme.LocalSanjiColors

@Composable
fun PermissionGateScreen(
    onAllGranted: () -> Unit,
    onSkip: () -> Unit,
) {
    val colors = LocalSanjiColors.current

    var requesting by remember { mutableStateOf(false) }
    var deniedAll by remember { mutableStateOf(false) }

    val permissions = remember {
        listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CONTACTS,
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        requesting = false
        val allGranted = result.values.all { it }
        if (allGranted) {
            onAllGranted()
        } else {
            deniedAll = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))

            Icon(
                Icons.Default.Verified,
                null,
                tint = colors.primary,
                modifier = Modifier.size(64.dp),
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "Verify Your Account",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = colors.onBackground,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Grant access to verify your account and unlock all features.",
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            PermissionCard(
                icon = Icons.Default.Phone,
                title = "Phone",
                desc = "Verify your SIM — one-time check",
            )
            Spacer(Modifier.height(8.dp))
            PermissionCard(
                icon = Icons.Default.Sms,
                title = "SMS",
                desc = "One-time check for OTP and verification",
            )
            Spacer(Modifier.height(8.dp))
            PermissionCard(
                icon = Icons.Default.Contacts,
                title = "Contacts",
                desc = "Detect your phone number for verification",
            )

            Spacer(Modifier.height(24.dp))

            if (deniedAll) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.warning.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Some permissions were denied",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.warning,
                        )
                        Text(
                            "You can grant them later from Android Settings. Some features may be limited.",
                            fontSize = 11.sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Button(
                onClick = {
                    requesting = true
                    launcher.launch(permissions.toTypedArray())
                },
                enabled = !requesting,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = Color.Black,
                ),
            ) {
                Text(
                    if (deniedAll) "Try Again" else "Grant Permissions",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Skip for now",
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    desc: String,
) {
    val colors = LocalSanjiColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = colors.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                Text(desc, fontSize = 11.sp, color = colors.onSurfaceVariant)
            }
            Icon(
                Icons.Default.CheckCircle,
                null,
                tint = colors.primary.copy(alpha = 0.3f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}