package com.vi5hnu.nightshield.widgets

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.vi5hnu.nightshield.NightShieldManager

@Composable
fun FilterOverlay(onTap: () -> Unit) {
    val canvasColor        by NightShieldManager.canvasColor.collectAsState()
    val intensity          by NightShieldManager.filterIntensity.collectAsState()
    val adaptiveMultiplier by NightShieldManager.adaptiveMultiplier.collectAsState()
    val dimLevel           by NightShieldManager.dimLevel.collectAsState()
    val temporarilyDisabled by NightShieldManager.filterTemporarilyDisabled.collectAsState()
    val gradualFade        by NightShieldManager.gradualFadeEnabled.collectAsState()

    // fadeMultiplier scales the tint. CRITICAL: the overlay is often created while the app is in
    // the background (a shake with the app closed), where the Compose recomposer may not run the
    // follow-up frames that a LaunchedEffect / self-driven animation needs — depending on those
    // left the overlay window present but drawing nothing ("widget on, filter not there"). So the
    // PRO gradual fade-IN ramp is driven from the service via a StateFlow ([NightShieldManager.
    // fadeMultiplier], 1f when there is no fade), which redraws reliably in the background. Only
    // the accessibility temp-disable still animates in Compose — it runs while the screen is on /
    // another app is foreground, where frames are reliable.
    val activationFade by NightShieldManager.fadeMultiplier.collectAsState()
    val tempDisableFade by animateFloatAsState(
        targetValue = if (temporarilyDisabled) 0f else 1f,
        animationSpec = if (gradualFade && !temporarilyDisabled)
            tween(durationMillis = 12_000, easing = LinearEasing) else snap(),
        label = "temp_disable_fade",
    )
    val fadeMultiplier = activationFade * tempDisableFade

    val displayAlpha = canvasColor.alpha * intensity * adaptiveMultiplier * fadeMultiplier
    val displayDim   = dimLevel * fadeMultiplier

    // Extra dim: a black layer under the colour tint, darkening below system-min brightness.
    if (displayDim > 0.01f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = displayDim))
        )
    }

    if (displayAlpha > 0.01f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(canvasColor.copy(alpha = displayAlpha))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onTap() }
        )
    }
}
