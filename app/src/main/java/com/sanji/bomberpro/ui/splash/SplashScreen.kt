package com.sanji.bomberpro.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val colors = LocalSanjiColors.current

    // Staged entry animations
    val logoScale = remember { Animatable(0.5f) }
    val logoAlpha = remember { Animatable(0f) }
    val subtitleAlpha = remember { Animatable(0f) }
    val taglineAlpha = remember { Animatable(0f) }
    val glowAlpha = remember { Animatable(0f) }

    // Ambient background pulse
    val transition = rememberInfiniteTransition(label = "splashAmbient")
    val glowShift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowShift",
    )

    // Rotating ring
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // Spin forever in background
        while (true) {
            rotation.animateTo(
                targetValue = rotation.value + 360f,
                animationSpec = tween(2400, easing = LinearEasing),
            )
        }
    }

    LaunchedEffect(Unit) {
        // 1. Glow ring appears + logo springs in
        glowAlpha.animateTo(1f, tween(300))
        logoAlpha.animateTo(1f, tween(250))
        logoScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.55f,
                stiffness = 240f,
            ),
        )

        // 2. Subtitle slides in
        delay(120)
        subtitleAlpha.animateTo(1f, tween(350))

        // 3. Tagline fades in
        delay(180)
        taglineAlpha.animateTo(1f, tween(400))

        // 4. Hold, then finish
        delay(850)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.background,
                        colors.background,
                        colors.primary.copy(alpha = 0.04f + 0.06f * glowShift),
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Ambient glow behind logo
        Box(
            modifier = Modifier
                .size(280.dp)
                .alpha(glowAlpha.value * 0.6f)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.primary.copy(alpha = 0.20f),
                            colors.primary.copy(alpha = 0.05f),
                            androidx.compose.ui.graphics.Color.Transparent,
                        ),
                        center = Offset.Unspecified,
                    ),
                    shape = CircleShape,
                )
        )

        // Rotating ring
        Box(
            modifier = Modifier
                .size(140.dp)
                .alpha(glowAlpha.value * 0.9f)
                .androidxRotate(rotation.value)
                .background(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            androidx.compose.ui.graphics.Color.Transparent,
                            colors.primary.copy(alpha = 0.55f),
                            colors.secondary.copy(alpha = 0.55f),
                            androidx.compose.ui.graphics.Color.Transparent,
                        ),
                        center = Offset.Unspecified,
                    ),
                    shape = CircleShape,
                )
        ) {
            // Inner cutout to create ring look
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.Center)
                    .background(colors.background, CircleShape),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = "SANJI",
                fontSize = 58.sp,
                fontWeight = FontWeight.Black,
                color = colors.onBackground,
                letterSpacing = (-1).sp,
                modifier = Modifier
                    .alpha(logoAlpha.value)
                    .scale(logoScale.value),
            )
            Text(
                text = "BOMBER",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 10.sp,
                color = colors.primary,
                modifier = Modifier
                    .alpha(subtitleAlpha.value)
                    .scale(logoScale.value * 0.99f),
            )
            Text(
                text = "PRO",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 14.sp,
                color = colors.secondary,
                modifier = Modifier
                    .alpha(subtitleAlpha.value)
                    .scale(logoScale.value * 0.99f),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "⚡ Fast. Private. Unlimited. ⚡",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.alpha(taglineAlpha.value),
            )
        }
    }
}

private fun Modifier.androidxRotate(degrees: Float): Modifier =
    androidx.compose.ui.graphics.graphicsLayer {
        rotationZ = degrees
    }