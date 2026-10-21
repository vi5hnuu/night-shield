package com.vi5hnu.nightshield.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.vi5hnu.nightshield.ui.theme.AdaptiveLayout

/**
 * Fills the available width up to [max], centred horizontally — full width on a phone, a readable
 * centred column on a tablet. Apply where a screen's content column is laid out.
 */
fun Modifier.readableWidth(max: Dp = AdaptiveLayout.contentMaxWidth): Modifier =
    this
        .fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = max)
        .fillMaxWidth()
