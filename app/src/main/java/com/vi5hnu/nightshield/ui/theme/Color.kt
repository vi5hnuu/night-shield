package com.vi5hnu.nightshield.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Palette definitions for every app theme.
 *
 * Each theme supplies only the tones that make it distinctive; [NightPalette.toColorScheme] derives
 * the full Material 3 scheme from them. That keeps the six palettes in sync — previously each theme
 * hand-listed ~20 roles, which is why some had no elevation hierarchy at all (one flat `surface`
 * plus one `surfaceVariant` for every card in the app).
 *
 * The `surfaceContainer*` ladder is what gives depth on a dark UI: dark themes read elevation as a
 * lighter fill, not as a drop shadow, so cards use container tones rather than `elevation`.
 */
data class NightPalette(
    // Brand
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    // Supporting accent (warnings, streaks, "needs attention" banners)
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    // Second accent (informational highlights)
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    // Surfaces, darkest → lightest
    val background: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    // Content
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
)

// Error tones are shared: a red that stays legible on every one of the dark palettes.
private val ErrorTone = Color(0xFFFFB4AB)
private val OnErrorTone = Color(0xFF690005)
private val ErrorContainerTone = Color(0xFF93000A)
private val OnErrorContainerTone = Color(0xFFFFDAD6)

/** Expands a palette into the full Material 3 dark scheme used by [NightShieldTheme]. */
fun NightPalette.toColorScheme(): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    inversePrimary = primaryContainer,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    background = background,
    onBackground = onSurface,
    // `surface` matches the background so screens read as one sheet; cards lift with containers.
    surface = background,
    onSurface = onSurface,
    surfaceVariant = surfaceContainerHigh,
    onSurfaceVariant = onSurfaceVariant,
    surfaceContainerLowest = surfaceContainerLowest,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,
    surfaceDim = surfaceContainerLowest,
    surfaceBright = surfaceContainerHighest,
    surfaceTint = primary,
    inverseSurface = onSurface,
    inverseOnSurface = background,
    outline = outline,
    outlineVariant = outlineVariant,
    error = ErrorTone,
    onError = OnErrorTone,
    errorContainer = ErrorContainerTone,
    onErrorContainer = OnErrorContainerTone,
    scrim = Color.Black,
)

// ── Midnight — the default indigo night palette ───────────────────────────────

val MidnightPalette = NightPalette(
    primary = Color(0xFF93A9FF),
    onPrimary = Color(0xFF12204D),
    primaryContainer = Color(0xFF2C3D77),
    onPrimaryContainer = Color(0xFFDCE3FF),
    secondary = Color(0xFFFFB870),
    onSecondary = Color(0xFF452B00),
    secondaryContainer = Color(0xFF5F3D00),
    onSecondaryContainer = Color(0xFFFFDDB8),
    tertiary = Color(0xFF7BD0EC),
    onTertiary = Color(0xFF00323F),
    tertiaryContainer = Color(0xFF11485A),
    onTertiaryContainer = Color(0xFFB9E9FB),
    background = Color(0xFF070A12),
    surfaceContainerLowest = Color(0xFF04060C),
    surfaceContainerLow = Color(0xFF0B0F18),
    surfaceContainer = Color(0xFF10141E),
    surfaceContainerHigh = Color(0xFF161B27),
    surfaceContainerHighest = Color(0xFF1C2231),
    onSurface = Color(0xFFE7EBF5),
    onSurfaceVariant = Color(0xFFA6B0C4),
    outline = Color(0xFF414B60),
    outlineVariant = Color(0xFF262E3D),
)

// ── PRO: Dark OLED — true black, saves power on OLED panels ───────────────────

val OledPalette = MidnightPalette.copy(
    background = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF070707),
    surfaceContainer = Color(0xFF0C0C0C),
    surfaceContainerHigh = Color(0xFF131313),
    surfaceContainerHighest = Color(0xFF1B1B1B),
    onSurface = Color(0xFFECEDEF),
    onSurfaceVariant = Color(0xFFA8ABB2),
    outline = Color(0xFF3B3B3D),
    outlineVariant = Color(0xFF232325),
)

// ── PRO: Warm — amber-tinted, matches the filter's own aesthetic ──────────────

val WarmPalette = NightPalette(
    primary = Color(0xFFFFB874),
    onPrimary = Color(0xFF452B00),
    primaryContainer = Color(0xFF6A4200),
    onPrimaryContainer = Color(0xFFFFDDB8),
    secondary = Color(0xFFFF9C6B),
    onSecondary = Color(0xFF551D00),
    secondaryContainer = Color(0xFF762C00),
    onSecondaryContainer = Color(0xFFFFDBCC),
    tertiary = Color(0xFFFFD08A),
    onTertiary = Color(0xFF432C00),
    tertiaryContainer = Color(0xFF614100),
    onTertiaryContainer = Color(0xFFFFE7C2),
    background = Color(0xFF100A03),
    surfaceContainerLowest = Color(0xFF0A0601),
    surfaceContainerLow = Color(0xFF150D04),
    surfaceContainer = Color(0xFF1B1207),
    surfaceContainerHigh = Color(0xFF23180C),
    surfaceContainerHighest = Color(0xFF2C1F11),
    onSurface = Color(0xFFF5E3CE),
    onSurfaceVariant = Color(0xFFC8B59E),
    outline = Color(0xFF5B4B36),
    outlineVariant = Color(0xFF33281A),
)

// ── PRO: Blue Night — calm navy for reading and late coding ───────────────────

val BlueNightPalette = NightPalette(
    primary = Color(0xFF8FC2FF),
    onPrimary = Color(0xFF00325A),
    primaryContainer = Color(0xFF14497C),
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFF7BD0EC),
    onSecondary = Color(0xFF00323F),
    secondaryContainer = Color(0xFF11485A),
    onSecondaryContainer = Color(0xFFB9E9FB),
    tertiary = Color(0xFFAFC7FF),
    onTertiary = Color(0xFF102F60),
    tertiaryContainer = Color(0xFF2A4578),
    onTertiaryContainer = Color(0xFFDAE2FF),
    background = Color(0xFF050A12),
    surfaceContainerLowest = Color(0xFF03070D),
    surfaceContainerLow = Color(0xFF091018),
    surfaceContainer = Color(0xFF0E1520),
    surfaceContainerHigh = Color(0xFF131C2A),
    surfaceContainerHighest = Color(0xFF192333),
    onSurface = Color(0xFFE2E9F5),
    onSurfaceVariant = Color(0xFFA3B3C7),
    outline = Color(0xFF3E4C60),
    outlineVariant = Color(0xFF23303F),
)

// ── PRO: Forest — deep emerald, easy on the eyes ──────────────────────────────

val ForestPalette = NightPalette(
    primary = Color(0xFF7EE0A5),
    onPrimary = Color(0xFF003920),
    primaryContainer = Color(0xFF105331),
    onPrimaryContainer = Color(0xFF9DF9C0),
    secondary = Color(0xFFBCE7A2),
    onSecondary = Color(0xFF23380F),
    secondaryContainer = Color(0xFF395023),
    onSecondaryContainer = Color(0xFFD8FFBD),
    tertiary = Color(0xFF9CD8C6),
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF1B4E46),
    onTertiaryContainer = Color(0xFFB8F5E2),
    background = Color(0xFF05100A),
    surfaceContainerLowest = Color(0xFF030B06),
    surfaceContainerLow = Color(0xFF08160D),
    surfaceContainer = Color(0xFF0C1C12),
    surfaceContainerHigh = Color(0xFF112418),
    surfaceContainerHighest = Color(0xFF172D1F),
    onSurface = Color(0xFFDCF3E3),
    onSurfaceVariant = Color(0xFFA6C2B0),
    outline = Color(0xFF3C5546),
    outlineVariant = Color(0xFF23342A),
)

// ── PRO: Purple Night — galaxy tones ──────────────────────────────────────────

val PurpleNightPalette = NightPalette(
    primary = Color(0xFFD3A8FF),
    onPrimary = Color(0xFF3D1560),
    primaryContainer = Color(0xFF562B7C),
    onPrimaryContainer = Color(0xFFEEDBFF),
    secondary = Color(0xFFF3ABE4),
    onSecondary = Color(0xFF4E1148),
    secondaryContainer = Color(0xFF692960),
    onSecondaryContainer = Color(0xFFFFD7F3),
    tertiary = Color(0xFFB8C2FF),
    onTertiary = Color(0xFF20296A),
    tertiaryContainer = Color(0xFF384182),
    onTertiaryContainer = Color(0xFFDFE0FF),
    background = Color(0xFF0A0714),
    surfaceContainerLowest = Color(0xFF06040E),
    surfaceContainerLow = Color(0xFF100B1C),
    surfaceContainer = Color(0xFF150F24),
    surfaceContainerHigh = Color(0xFF1C142E),
    surfaceContainerHighest = Color(0xFF241B39),
    onSurface = Color(0xFFECE3F7),
    onSurfaceVariant = Color(0xFFBAABCA),
    outline = Color(0xFF514463),
    outlineVariant = Color(0xFF302840),
)
