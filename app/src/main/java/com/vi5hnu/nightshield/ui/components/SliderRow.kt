package com.vi5hnu.nightshield.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vi5hnu.nightshield.ui.theme.Spacing

/**
 * A labelled, full-width slider row.
 *
 * The old layout squeezed sliders into a 120dp trailing slot next to a title and subtitle, which
 * made them fiddly to drag and gave no read-out of the current value. Here the label and value sit
 * above a full-width track.
 *
 * @param valueLabel formatted current value, shown right-aligned against the title.
 * @param onValueChangeFinished commit point — persist here, not on every frame of the drag.
 */
@Composable
fun SliderRow(
    @DrawableRes icon: Int,
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    onValueChangeFinished: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingRow(
            icon = icon,
            title = title,
            subtitle = subtitle,
            enabled = enabled,
            trailing = {
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                )
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            )
        }
    }
}
