package com.vi5hnu.nightshield.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ColorDot(
    color: Color,
    sizeDp: Dp = 36.dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(sizeDp)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .clickable { onClick() }
            .semantics { contentDescription = "Current filter color. Tap to change." }
    )
}
