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

    // Target alpha: 0 when paused (per-app), full value otherwise
    val targetAlpha = if (temporarilyDisabled) 0f else canvasColor.alpha * intensity

    // PRO: When gradual fade is enabled, animate in over 12 s; fade out is instant.
    // This means transitioning from disabled→enabled feels gentle, while pausing
    // per-app feels immediate and doesn't confuse the user.
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = when {
            gradualFade && !temporarilyDisabled -> tween(durationMillis = 12_000, easing = LinearEasing)
            else -> snap()
        },
        label = "filter_alpha",
    )

    if (animatedAlpha > 0.01f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(canvasColor.copy(alpha = animatedAlpha))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onTap() }
        )
    }
}
