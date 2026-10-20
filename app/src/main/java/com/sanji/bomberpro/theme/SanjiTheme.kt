package com.sanji.bomberpro.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class AppTheme(
    val id: String,
    val displayName: String,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val background: Color,
    val surface: Color,
    val onBackground: Color,
    val isDark: Boolean,
) {
    NEON_CIPHER(
        id = "neon_cipher",
        displayName = "Neon Cipher",
        primary = Color(0xFF00E5FF),
        secondary = Color(0xFFFF00AA),
        accent = Color(0xFFB026FF),
        background = Color(0xFF000000),
        surface = Color(0xFF0F0F14),
        onBackground = Color(0xFFE6F7FF),
        isDark = true,
    ),
    INFERNO(
        id = "inferno",
        displayName = "Inferno",
        primary = Color(0xFFFF4500),
        secondary = Color(0xFFFF0033),
        accent = Color(0xFFFFB800),
        background = Color(0xFF0A0A0A),
        surface = Color(0xFF1A0F0A),
        onBackground = Color(0xFFFFEEE0),
        isDark = true,
    ),
    MIDNIGHT_AMETHYST(
        id = "midnight_amethyst",
        displayName = "Midnight Amethyst",
        primary = Color(0xFF7C3AED),
        secondary = Color(0xFFEC4899),
        accent = Color(0xFFA78BFA),
        background = Color(0xFF0D0417),
        surface = Color(0xFF1A0B2E),
        onBackground = Color(0xFFF3E8FF),
        isDark = true,
    ),
    MATRIX_ZERO(
        id = "matrix_zero",
        displayName = "Matrix Zero",
        primary = Color(0xFF00FF41),
        secondary = Color(0xFF008F11),
        accent = Color(0xFF00B32C),
        background = Color(0xFF000000),
        surface = Color(0xFF0A1A0A),
        onBackground = Color(0xFFC6FFC6),
        isDark = true,
    ),
    OCEAN_DEPTHS(
        id = "ocean_depths",
        displayName = "Ocean Depths",
        primary = Color(0xFF06B6D4),
        secondary = Color(0xFF0369A1),
        accent = Color(0xFF14B8A6),
        background = Color(0xFF06121E),
        surface = Color(0xFF0C1E2E),
        onBackground = Color(0xFFE0F7FF),
        isDark = true,
    ),
    PAPER_LITE(
        id = "paper_lite",
        displayName = "Paper Lite",
        primary = Color(0xFF1E40AF),
        secondary = Color(0xFF0EA5E9),
        accent = Color(0xFF10B981),
        background = Color(0xFFF8FAFC),
        surface = Color(0xFFFFFFFF),
        onBackground = Color(0xFF0F172A),
        isDark = false,
    ),
}

data class SanjiColors(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val background: Color,
    val surface: Color,
    val onBackground: Color,
    val onSurfaceVariant: Color,
    val success: Color = Color(0xFF22C55E),
    val warning: Color = Color(0xFFF59E0B),
    val error: Color = Color(0xFFEF4444),
)

val LocalSanjiColors = staticCompositionLocalOf {
    SanjiColors(
        primary = AppTheme.NEON_CIPHER.primary,
        secondary = AppTheme.NEON_CIPHER.secondary,
        accent = AppTheme.NEON_CIPHER.accent,
        background = AppTheme.NEON_CIPHER.background,
        surface = AppTheme.NEON_CIPHER.surface,
        onBackground = AppTheme.NEON_CIPHER.onBackground,
        onSurfaceVariant = AppTheme.NEON_CIPHER.onBackground.copy(alpha = 0.7f),
    )
}

private val SpaceGrotesk = FontFamily.SansSerif
private val Inter = FontFamily.SansSerif
private val JetBrainsMono = FontFamily.Monospace

private val SanjiTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
    ),
)

@Composable
fun SanjiTheme(
    theme: AppTheme,
    content: @Composable () -> Unit,
) {
    val scheme = if (theme.isDark) {
        darkColorScheme(
            primary = theme.primary,
            secondary = theme.secondary,
            tertiary = theme.accent,
            background = theme.background,
            surface = theme.surface,
            onPrimary = Color.Black,
            onSecondary = Color.Black,
            onTertiary = Color.Black,
            onBackground = theme.onBackground,
            onSurface = theme.onBackground,
            error = Color(0xFFEF4444),
        )
    } else {
        lightColorScheme(
            primary = theme.primary,
            secondary = theme.secondary,
            tertiary = theme.accent,
            background = theme.background,
            surface = theme.surface,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.White,
            onBackground = theme.onBackground,
            onSurface = theme.onBackground,
            error = Color(0xFFDC2626),
        )
    }

    val sanjiColors = SanjiColors(
        primary = theme.primary,
        secondary = theme.secondary,
        accent = theme.accent,
        background = theme.background,
        surface = theme.surface,
        onBackground = theme.onBackground,
        onSurfaceVariant = theme.onBackground.copy(alpha = 0.7f),
    )

    CompositionLocalProvider(LocalSanjiColors provides sanjiColors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = SanjiTypography,
            content = content,
        )
    }
}