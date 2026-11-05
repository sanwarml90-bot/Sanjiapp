package com.sanji.bomberpro.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanji.bomberpro.theme.LocalSanjiColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

object SanjiAnim {
    const val INSTANT = 80
    const val FAST = 160
    const val NORMAL = 260
    const val SLOW = 400
    const val HERO = 620
    const val BREATHE = 1600
    const val SHIMMER = 1200
    const val GLOW = 2200
    const val STAGGER = 55
    val smoothEasing = FastOutSlowInEasing
    fun <T> pressSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow,
    )
    fun <T> gentleSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}

object SanjiEmoji {
    const val HOME = "🏠"
    const val BLAST = "🚀"
    const val ADMIN = "🛡️"
    const val OTP = "🔐"
    const val HISTORY = "📜"
    const val WALLET = "💳"
    const val SUCCESS = "✅"
    const val ERROR = "❌"
    const val WARNING = "⚠️"
    const val INFO = "ℹ️"
    const val PENDING = "⏳"
    const val LOADING = "⏬"
    const val LOCK = "🔒"
    const val UNLOCK = "🔓"
    const val CREDITS = "💰"
    const val COINS = "🪙"
    const val GIFT = "🎁"
    const val TRANSFER = "🔁"
    const val RECEIPT = "🧾"
    const val SEND = "📤"
    const val RECEIVE = "📥"
    const val COPY = "📋"
    const val REFRESH = "🔄"
    const val DELETE = "🗑️"
    const val EDIT = "✏️"
    const val ADD = "➕"
    const val SEARCH = "🔍"
    const val FILTER = "🎚️"
    const val SHARE = "🔗"
    const val DOWNLOAD = "⬇️"
    const val UPLOAD = "⬆️"
    const val PHONE = "📞"
    const val MESSAGE = "💬"
    const val DEVICE = "📱"
    const val FIREBASE = "🔥"
    const val NETWORK = "📡"
    const val BATTERY = "🔋"
    const val SETTINGS = "⚙️"
    const val PREMIUM = "💎"
    const val STAR = "⭐"
    const val FIRE = "🔥"
    const val ROCKET = "🚀"
    const val TROPHY = "🏆"
    const val CROWN = "👑"
    const val SHIELD = "🛡️"
    const val KEY = "🔑"
    const val BELL = "🔔"
    const val MEGAPHONE = "📢"
    const val USER = "👤"
    const val USERS = "👥"
    const val ADMIN_ROLE = "🔱"
    const val OWNER = "👑"
    const val BANNED = "🚫"
    const val NEW = "🆕"
    const val TOTAL = "📊"
    const val CHART = "📈"
    const val CLOCK = "🕐"
    const val CALENDAR = "📅"
    const val HEART = "❤️"
    const val SPARKLE = "✨"
    const val BOLT = "⚡"
    const val PARTY = "🎉"
    const val WAVE = "👋"
}

fun Modifier.pressScale(): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = SanjiAnim.pressSpring(),
        label = "pressScale",
    )
    this.scale(scale)
}

fun Modifier.fadeSlideIn(
    delayMs: Int = 0,
    startOffsetY: Int = 24,
): Modifier = composed {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(SanjiAnim.NORMAL, easing = SanjiAnim.smoothEasing),
        label = "fadeIn",
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else startOffsetY.toFloat(),
        animationSpec = tween(SanjiAnim.HERO, easing = SanjiAnim.smoothEasing),
        label = "slideIn",
    )
    this
        .alpha(alpha)
        .graphicsLayer { translationY = offsetY }
}

fun Modifier.pulse(minAlpha: Float = 0.4f, maxAlpha: Float = 1f): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = minAlpha,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(SanjiAnim.BREATHE, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )
    this.alpha(alpha)
}

fun Modifier.shimmer(
    baseColor: Color = Color(0xFF1A1A1A),
    highlightColor: Color = Color(0xFF2A2A2A),
    shape: Shape = RoundedCornerShape(12.dp),
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(SanjiAnim.SHIMMER, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    val brush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(progress - 400f, 0f),
        end = Offset(progress, 0f),
    )
    this
        .clip(shape)
        .background(brush)
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    width: Dp? = null,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    val colors = LocalSanjiColors.current
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .shimmer(
                baseColor = colors.surface,
                highlightColor = colors.primary.copy(alpha = 0.08f),
                shape = shape,
            )
    )
}

@Composable
fun AnimatedCounter(
    value: Int,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.ui.text.TextStyle.Default,
    color: Color = LocalSanjiColors.current.onBackground,
    fontWeight: FontWeight = FontWeight.Bold,
    fontFamily: FontFamily = FontFamily.Monospace,
) {
    val animated = remember { Animatable(value.toFloat()) }
    LaunchedEffect(value) {
        animated.animateTo(
            targetValue = value.toFloat(),
            animationSpec = tween(SanjiAnim.SLOW, easing = FastOutSlowInEasing),
        )
    }
    Text(
        text = animated.value.roundToInt().toString(),
        modifier = modifier,
        style = style,
        color = color,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
    )
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emoji: String? = null,
    loading: Boolean = false,
    height: Dp = 56.dp,
    shape: Shape = RoundedCornerShape(16.dp),
) {
    val colors = LocalSanjiColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = SanjiAnim.pressSpring(),
        label = "gradBtnScale",
    )
    Box(
        modifier = modifier
            .height(height)
            .scale(scale)
            .clip(shape)
            .background(
                brush = if (enabled) {
                    Brush.linearGradient(listOf(colors.primary, colors.secondary))
                } else {
                    Brush.linearGradient(listOf(colors.surface, colors.surface))
                },
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Button(
            onClick = onClick,
            enabled = enabled && !loading,
            interactionSource = interaction,
            modifier = Modifier.fillMaxSize(),
            shape = shape,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = if (enabled) Color.Black else colors.onSurfaceVariant,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = colors.onSurfaceVariant,
            ),
        ) {
            if (loading) {
                TypingDots(size = 8.dp, color = if (enabled) Color.Black else colors.onSurfaceVariant)
            } else {
                if (emoji != null) {
                    Text(emoji, fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.3.sp,
                )
            }
        }
    }
}

@Composable
fun EmojiChip(
    emoji: String,
    label: String,
    modifier: Modifier = Modifier,
    color: Color = LocalSanjiColors.current.primary,
    active: Boolean = true,
) {
    val colors = LocalSanjiColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (active) color.copy(alpha = 0.15f) else colors.surface,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 12.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (active) color else colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SuccessBurst(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    val colors = LocalSanjiColors.current
    val scale = remember { Animatable(0.3f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        alpha.animateTo(1f, tween(SanjiAnim.SLOW))
    }
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(colors.success.copy(alpha = 0.15f * alpha.value))
                .pulse(0.6f, 1f),
        )
        Text(
            text = "✅",
            fontSize = (size.value * 0.55f).sp,
            modifier = Modifier.scale(scale.value),
        )
    }
}

@Composable
fun ErrorShake(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    val colors = LocalSanjiColors.current
    val shake = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        repeat(4) {
            shake.animateTo(10f, tween(60))
            shake.animateTo(-10f, tween(60))
        }
        shake.animateTo(0f, tween(80))
    }
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationX = shake.value },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(colors.error.copy(alpha = 0.15f)),
        )
        Text(
            text = "❌",
            fontSize = (size.value * 0.55f).sp,
        )
    }
}

@Composable
fun RotatingGlowRing(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    strokeWidth: Dp = 4.dp,
) {
    val colors = LocalSanjiColors.current
    val transition = rememberInfiniteTransition(label = "glowRing")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "glowAngle",
    )
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .graphicsLayer { rotationZ = angle }
            .background(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        colors.primary.copy(alpha = 0.7f),
                        colors.secondary.copy(alpha = 0.7f),
                        Color.Transparent,
                    ),
                    center = Offset.Unspecified,
                )
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(strokeWidth)
                .clip(CircleShape)
                .background(colors.background),
        )
    }
}

@Composable
fun TypingDots(
    size: Dp = 10.dp,
    color: Color = LocalSanjiColors.current.primary,
) {
    val transition = rememberInfiniteTransition(label = "typing")
    val alpha1 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(500, delayMillis = 0, easing = LinearEasing),
            RepeatMode.Reverse,
        ),
        label = "dot1",
    )
    val alpha2 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(500, delayMillis = 140, easing = LinearEasing),
            RepeatMode.Reverse,
        ),
        label = "dot2",
    )
    val alpha3 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(500, delayMillis = 280, easing = LinearEasing),
            RepeatMode.Reverse,
        ),
        label = "dot3",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = alpha1)))
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = alpha2)))
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = alpha3)))
    }
}

@Composable
fun AnimatedGradientBg(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalSanjiColors.current
    val transition = rememberInfiniteTransition(label = "gradBg")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shift",
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.background,
                        colors.primary.copy(alpha = 0.05f + 0.03f * shift),
                        colors.background,
                        colors.secondary.copy(alpha = 0.04f + 0.03f * shift),
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(1000f * shift, 1000f * shift),
                )
            )
    ) {
        content()
    }
}

@Composable
fun StaggeredColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        content()
    }
}

@Composable
fun EmojiBullet(
    emoji: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSanjiColors.current
    Row(
        modifier = modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(emoji, fontSize = 13.sp, modifier = Modifier.width(22.dp))
        Text(
            text,
            fontSize = 12.sp,
            color = colors.onBackground,
            lineHeight = 17.sp,
        )
    }
}

@Composable
fun SectionHeader(
    emoji: String,
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = LocalSanjiColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onBackground,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

@Composable
fun FadeSlideVisibility(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(SanjiAnim.NORMAL)) +
                slideInVertically(
                    animationSpec = tween(SanjiAnim.SLOW, easing = FastOutSlowInEasing),
                    initialOffsetY = { -it / 6 },
                ),
        exit = fadeOut(tween(SanjiAnim.FAST)) +
                slideOutVertically(
                    animationSpec = tween(SanjiAnim.FAST),
                    targetOffsetY = { -it / 6 },
                ),
    ) {
        content()
    }
}

@Composable
fun GlowBorder(
    modifier: Modifier = Modifier,
    color: Color = LocalSanjiColors.current.primary,
    radius: Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "glowBorder")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            tween(SanjiAnim.GLOW, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(color.copy(alpha = alpha * 0.15f))
            .padding(1.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(radius - 1.dp))
                .background(LocalSanjiColors.current.surface),
        ) {
            content()
        }
    }
}

@Composable
fun AnimatedBadge(
    count: Int,
    modifier: Modifier = Modifier,
    color: Color = LocalSanjiColors.current.error,
) {
    val visible = count > 0
    val scale = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible) {
        scale.animateTo(if (visible) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    if (visible) {
        Box(
            modifier = modifier
                .scale(scale.value)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (count > 9) "9+" else count.toString(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
fun BobbingEmoji(
    emoji: String,
    fontSize: TextUnit = 32.sp,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "bob")
    val offsetY by transition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            tween(SanjiAnim.BREATHE, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "bobOffset",
    )
    Text(
        text = emoji,
        fontSize = fontSize,
        modifier = modifier.graphicsLayer { translationY = offsetY },
    )
}

@Composable
fun HeroIcon(
    icon: ImageVector,
    tint: Color = LocalSanjiColors.current.primary,
    size: Dp = 64.dp,
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(0.7f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = modifier
            .size(size)
            .scale(scale.value)
            .pulse(0.85f, 1f),
    )
}