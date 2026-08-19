package com.vi5hnu.nightshield.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.vi5hnu.nightshield.ui.theme.Radius
import com.vi5hnu.nightshield.ui.theme.Spacing

/**
 * A row of mutually exclusive options — the app's one selection control.
 *
 * Generic over the option type so shake sensitivity, widget style and any future picker share the
 * same look and animation instead of each hand-rolling a `Surface` + `Row` (the pattern that had
 * emoji labels bolted on for lack of icons).
 */
@Composable
fun <T> SegmentedChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val container by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                animationSpec = tween(180),
                label = "chip_container",
            )
            val content by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(180),
                label = "chip_content",
            )
            Surface(
                onClick = { onSelect(option) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(Radius.sm),
                color = container.copy(alpha = if (enabled) 1f else 0.5f),
                contentColor = content,
            ) {
                Box(
                    modifier = Modifier.padding(vertical = Spacing.md, horizontal = Spacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
