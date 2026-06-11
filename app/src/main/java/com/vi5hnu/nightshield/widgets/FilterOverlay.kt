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
import com.vi5hnu.nightshield.NightShieldManager

@Composable
fun FilterOverlay(onTap: () -> Unit) {
    val canvasColor        by NightShieldManager.canvasColor.collectAsState()
    val intensity          by NightShieldManager.filterIntensity.collectAsState()
    val temporarilyDisabled by NightShieldManager.filterTemporarilyDisabled.collectAsState()
    val gradualFade        by NightShieldManager.gradualFadeEnabled.collectAsState()

    // Starts false so the overlay begins fully transparent on first composition,
    // then immediately flips to true to trigger the fade-in animation.
    var overlayMounted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { overlayMounted = true }

    // PRO: Gradual fade animates both the initial "Activate" (overlayMounted: false→true)
    // and the accessibility-service temp-disable (temporarilyDisabled: false→true→false).
    // Intensity and color changes are applied instantly so the slider stays responsive.
    val fadeMultiplier by animateFloatAsState(
        targetValue = if (!overlayMounted || temporarilyDisabled) 0f else 1f,
        animationSpec = when {
            gradualFade && overlayMounted && !temporarilyDisabled ->
                tween(durationMillis = 12_000, easing = LinearEasing)
            else -> snap()
        },
        label = "fade_multiplier",
    )

    val displayAlpha = canvasColor.alpha * intensity * fadeMultiplier

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
