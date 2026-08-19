package com.vi5hnu.nightshield.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.vi5hnu.nightshield.NightShieldManager

/**
 * Maps a user-selected theme to its palette.
 *
 * Exposed (rather than kept private to [NightShieldTheme]) so the theme picker can paint an
 * accurate swatch for each option without duplicating any colour values.
 * Returns `null` for [NightShieldManager.AppTheme.DYNAMIC], whose colours come from the wallpaper
 * at runtime and therefore cannot be described by a static palette.
 */
fun paletteFor(theme: NightShieldManager.AppTheme): NightPalette? = when (theme) {
    NightShieldManager.AppTheme.SYSTEM       -> MidnightPalette
    NightShieldManager.AppTheme.DYNAMIC      -> null
    NightShieldManager.AppTheme.DARK_OLED    -> OledPalette
    NightShieldManager.AppTheme.WARM         -> WarmPalette
    NightShieldManager.AppTheme.BLUE_NIGHT   -> BlueNightPalette
    NightShieldManager.AppTheme.FOREST       -> ForestPalette
    NightShieldManager.AppTheme.PURPLE_NIGHT -> PurpleNightPalette
}

/**
 * The app is dark-only by design — it is a night-time eye-comfort tool, and a light scheme would
 * fight the product's purpose. Every palette is therefore a dark scheme.
 */
@Composable
fun NightShieldTheme(
    theme: NightShieldManager.AppTheme = NightShieldManager.AppTheme.SYSTEM,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme: ColorScheme = when {
        // Material You — wallpaper-derived colours on Android 12+, else the default palette.
        theme == NightShieldManager.AppTheme.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicDarkColorScheme(context)

        else -> (paletteFor(theme) ?: MidnightPalette).toColorScheme()
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = NightShieldTypography,
        content = content,
    )
}
