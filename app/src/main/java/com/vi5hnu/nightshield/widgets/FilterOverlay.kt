package com.vi5hnu.nightshield.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.vi5hnu.nightshield.NightShieldManager

@Composable
fun FilterOverlay(onTap: () -> Unit) {
    val canvasColor by NightShieldManager.canvasColor.collectAsState()
    val intensity by NightShieldManager.filterIntensity.collectAsState()
    val temporarilyDisabled by NightShieldManager.filterTemporarilyDisabled.collectAsState()

    if (!temporarilyDisabled) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(canvasColor.copy(alpha = canvasColor.alpha * intensity))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onTap() }
        )
    }
}
