package com.sanji.bomberpro.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val colors = LocalSanjiColors.current
    val scale = remember { Animatable(0.6f) }
    val alpha = remember { Animatable(0f) }
    val taglineAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        )
        alpha.animateTo(1f, tween(400))
        taglineAlpha.animateTo(1f, tween(500))
        delay(700)
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
                        colors.primary.copy(alpha = 0.10f),
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.scale(scale.value),
        ) {
            Text(
                text = "SANJI",
                fontSize = 54.sp,
                fontWeight = FontWeight.Black,
                color = colors.onBackground,
                modifier = Modifier.alpha(alpha.value),
            )
            Text(
                text = "BOMBER PRO",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 8.sp,
                color = colors.primary,
                modifier = Modifier.alpha(alpha.value),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Fast. Private. Unlimited.",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.alpha(taglineAlpha.value),
            )
        }
    }
}