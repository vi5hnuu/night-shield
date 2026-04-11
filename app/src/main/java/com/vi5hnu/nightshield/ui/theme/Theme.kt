package com.vi5hnu.nightshield.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.vi5hnu.nightshield.NightShieldManager

// ── Default theme (deep indigo background) ────────────────────────────────────

private val SystemColorScheme = darkColorScheme(
    primary = IndigoCore,
    onPrimary = Color.White,
    primaryContainer = IndigoDim,
    onPrimaryContainer = IndigoLight,
    secondary = AmberWarm,
    onSecondary = Color.White,
    tertiary = CoolCyan,
    onTertiary = Color.White,
    background = BackgroundDeep,
    onBackground = TextPrimary,
    surface = BackgroundCard,
    onSurface = TextPrimary,
    surfaceVariant = BackgroundElevated,
    onSurfaceVariant = TextSecondary,
    outline = TextSecondary,
    error = ErrorRed,
    onError = Color.White,
)

// ── PRO: Dark OLED (pure black — saves battery on OLED screens) ───────────────

private val OledColorScheme = darkColorScheme(
    primary = IndigoCore,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0D0D1A),
    onPrimaryContainer = IndigoLight,
    secondary = AmberWarm,
    onSecondary = Color.White,
    tertiary = CoolCyan,
    onTertiary = Color.White,
    background = Color.Black,
    onBackground = TextPrimary,
    surface = Color(0xFF0A0A0A),
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF111111),
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF2A2A2A),
    error = ErrorRed,
    onError = Color.White,
)

// ── PRO: Warm (amber-tinted dark — matches night filter aesthetic) ─────────────

private val WarmColorScheme = darkColorScheme(
    primary = AmberWarm,
    onPrimary = Color(0xFF1A0A00),
    primaryContainer = Color(0xFF2D1800),
    onPrimaryContainer = Color(0xFFFFCC80),
    secondary = Color(0xFFFF6B35),
    onSecondary = Color.White,
    tertiary = Color(0xFFFFB347),
    onTertiary = Color(0xFF1A0A00),
    background = Color(0xFF120A00),
    onBackground = Color(0xFFFFE0B2),
    surface = Color(0xFF1E1000),
    onSurface = Color(0xFFFFE0B2),
    surfaceVariant = Color(0xFF2D1800),
    onSurfaceVariant = Color(0xFFFFCC80),
    outline = Color(0xFF4A2800),
    error = ErrorRed,
    onError = Color.White,
)

// ── PRO: Blue Night (deep navy — calm coding / reading vibe) ──────────────────

private val BlueNightColorScheme = darkColorScheme(
    primary = BlueNightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = BlueNightAccent,
    secondary = CoolCyan,
    onSecondary = Color.White,
    tertiary = Color(0xFF93C5FD),
    onTertiary = Color(0xFF031D4A),
    background = BlueNightBg,
    onBackground = TextPrimary,
    surface = BlueNightCard,
    onSurface = TextPrimary,
    surfaceVariant = BlueNightElevated,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF1E3A5F),
    error = ErrorRed,
    onError = Color.White,
)

// ── PRO: Forest (deep emerald — calm, eye-friendly green) ─────────────────────

private val ForestColorScheme = darkColorScheme(
    primary = ForestPrimary,
    onPrimary = Color(0xFF052E16),
    primaryContainer = Color(0xFF14532D),
    onPrimaryContainer = ForestAccent,
    secondary = Color(0xFF4ADE80),
    onSecondary = Color(0xFF052E16),
    tertiary = Color(0xFFA3E635),
    onTertiary = Color(0xFF14532D),
    background = ForestBg,
    onBackground = Color(0xFFD1FAE5),
    surface = ForestCard,
    onSurface = Color(0xFFD1FAE5),
    surfaceVariant = ForestElevated,
    onSurfaceVariant = Color(0xFF6EE7B7),
    outline = Color(0xFF14532D),
    error = ErrorRed,
    onError = Color.White,
)

// ── PRO: Purple Night (galaxy / AMOLED purple) ────────────────────────────────

private val PurpleNightColorScheme = darkColorScheme(
    primary = PurplePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B0764),
    onPrimaryContainer = PurpleAccent,
    secondary = Color(0xFFC084FC),
    onSecondary = Color.White,
    tertiary = Color(0xFFE879F9),
    onTertiary = Color(0xFF3B0764),
    background = PurpleBg,
    onBackground = Color(0xFFF3E8FF),
    surface = PurpleCard,
    onSurface = Color(0xFFF3E8FF),
    surfaceVariant = PurpleElevated,
    onSurfaceVariant = Color(0xFFD8B4FE),
    outline = Color(0xFF3B0764),
    error = ErrorRed,
    onError = Color.White,
)

// ── Composable ────────────────────────────────────────────────────────────────

@Composable
fun NightShieldTheme(
    theme: NightShieldManager.AppTheme = NightShieldManager.AppTheme.SYSTEM,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (theme) {
        NightShieldManager.AppTheme.SYSTEM       -> SystemColorScheme
        NightShieldManager.AppTheme.DARK_OLED    -> OledColorScheme
        NightShieldManager.AppTheme.WARM         -> WarmColorScheme
        NightShieldManager.AppTheme.BLUE_NIGHT   -> BlueNightColorScheme
        NightShieldManager.AppTheme.FOREST       -> ForestColorScheme
        NightShieldManager.AppTheme.PURPLE_NIGHT -> PurpleNightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = NightShieldTypography,
        content = content,
    )
}
