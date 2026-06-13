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

    // fadeMultiplier scales the tint. CRITICAL: when gradual fade is off (the default), it must be
    // full strength on the VERY FIRST composition — the overlay is often created while the app is
    // in the background (a shake with the app closed), where the Compose recomposer may not run the
    // follow-up frames that a LaunchedEffect / animation needs. Depending on those left the overlay
    // window present but drawing nothing ("widget on, filter not there"). So only the PRO gradual
    // fade-in uses the animated path; everything else renders immediately.
    val fadeMultiplier = if (gradualFade) {
        // PRO: animate the initial activate (mounted false→true) and accessibility temp-disable.
        var overlayMounted by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { overlayMounted = true }
        val anim by animateFloatAsState(
            targetValue = if (!overlayMounted || temporarilyDisabled) 0f else 1f,
            animationSpec = if (overlayMounted && !temporarilyDisabled)
                tween(durationMillis = 12_000, easing = LinearEasing) else snap(),
            label = "fade_multiplier",
        )
        anim
    } else {
        // No fade: render the tint immediately (no dependency on post-composition frames).
        if (temporarilyDisabled) 0f else 1f
    }

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
