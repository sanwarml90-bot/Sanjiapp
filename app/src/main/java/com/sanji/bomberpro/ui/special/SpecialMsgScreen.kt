package com.sanji.bomberpro.ui.special

import android.content.Intent
import android.net.Uri
import android.text.Html
import android.widget.TextView
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sanji.bomberpro.data.SpecialMessage
import com.sanji.bomberpro.services.SpecialMsgService
import com.sanji.bomberpro.theme.LocalSanjiColors
import com.sanji.bomberpro.ui.components.SanjiEmoji
import com.sanji.bomberpro.ui.components.fadeSlideIn
import com.sanji.bomberpro.ui.components.pressScale
import kotlinx.coroutines.launch

/**
 * Full-page special message screen shown to user on app open (once per version).
 * Close via X → marks as seen. Optionally has CTA button.
 *
 * Renders HTML via AndroidView/TextView when `isHtml=true`. Otherwise plain Text.
 */
@Composable
fun SpecialMsgScreen(
    uid: String,
    message: SpecialMessage,
    onDismiss: () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { SpecialMsgService() }

    var dismissing by remember { mutableStateOf(false) }

    fun handleDismiss() {
        if (dismissing) return
        dismissing = true
        scope.launch {
            try {
                service.markSeen(uid, message.version)
            } catch (_: Exception) {}
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.background,
                        colors.background,
                        colors.primary.copy(alpha = 0.10f),
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            // Top bar with X
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fadeSlideIn(delayMs = 0),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ANNOUNCEMENT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                    color = colors.primary,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    onClick = { handleDismiss() },
                    shape = CircleShape,
                    color = colors.surface,
                    modifier = Modifier.size(40.dp).pressScale(),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.onBackground,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Content card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colors.surface,
                modifier = Modifier.fillMaxWidth().fadeSlideIn(delayMs = 60),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(SanjiEmoji.MEGAPHONE, fontSize = 22.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = message.title.ifBlank { "Update" },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.onBackground,
                        )
                    }
                    Spacer(Modifier.height(14.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        if (message.isHtml && message.bodyHtml.isNotBlank()) {
                            AndroidView(
                                factory = { ctx ->
                                    TextView(ctx).apply {
                                        setTextColor(
                                            android.graphics.Color.parseColor(
                                                "#" + Integer.toHexString(
                                                    colors.onBackground.value.toInt()
                                                )
                                            )
                                        )
                                        textSize = 14f
                                        setLineSpacing(0f, 1.25f)
                                    }
                                },
                                update = { tv ->
                                    tv.text = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                        Html.fromHtml(message.bodyHtml, Html.FROM_HTML_MODE_LEGACY)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        Html.fromHtml(message.bodyHtml)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text(
                                text = message.body.ifBlank { message.bodyHtml },
                                fontSize = 14.sp,
                                color = colors.onBackground,
                                lineHeight = 20.sp,
                            )
                        }
                    }

                    // Optional CTA
                    if (message.buttonText.isNotBlank() && message.buttonUrl.isNotBlank()) {
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(message.buttonUrl))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp).pressScale(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primary,
                                contentColor = Color.Black,
                            ),
                        ) {
                            Text(
                                message.buttonText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Bottom hint
            Text(
                text = "Tap ${SanjiEmoji.SUCCESS} or X to continue",
                fontSize = 10.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { handleDismiss() },
                enabled = !dismissing,
                modifier = Modifier.fillMaxWidth().height(52.dp).pressScale(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = Color.Black,
                ),
            ) {
                Text("Continue ${SanjiEmoji.SUCCESS}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}