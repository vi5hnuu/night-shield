package com.vi5hnu.nightshield.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NightShieldColorScheme = darkColorScheme(
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

@Composable
fun NightShieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NightShieldColorScheme,
        typography = NightShieldTypography,
        content = content
    )
}
