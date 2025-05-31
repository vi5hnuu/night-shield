package com.vi5hnu.nightshield.widgets

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Tile(
    @DrawableRes id: Int,
    title: String,
    subtitle: String = "",
    suffix: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            DrawableIcon(id = id)
            Column {
                Text(text = title, color = MaterialTheme.colorScheme.outline)
                if (subtitle.isNotBlank()) Text(
                    text = subtitle,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                )
            }
        }
        suffix()
    }
}