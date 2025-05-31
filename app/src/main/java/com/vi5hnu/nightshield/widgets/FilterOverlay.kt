package com.vi5hnu.nightshield.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.vi5hnu.nightshield.NightShieldManager

@Composable
fun FilterOverlay(onClick:()-> Unit) {
    val canvasColor by NightShieldManager.canvasColor.collectAsState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick={onClick()})
            .background(canvasColor)
    ) {
    }
}