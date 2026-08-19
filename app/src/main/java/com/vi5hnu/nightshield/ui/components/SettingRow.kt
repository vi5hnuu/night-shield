package com.vi5hnu.nightshield.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vi5hnu.nightshield.ui.theme.IconSize
import com.vi5hnu.nightshield.ui.theme.Radius
import com.vi5hnu.nightshield.ui.theme.Spacing

/**
 * Leading icon in its own tinted container.
 *
 * Giving every row's icon the same 40dp box aligns all titles on one optical grid and keeps the
 * icon readable against the row's fill.
 */
@Composable
fun RowIcon(
    @DrawableRes id: Int,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    Box(
        modifier = modifier
            .size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.sm),
            color = container,
            modifier = Modifier.size(40.dp),
        ) {}
        Icon(
            painter = painterResource(id),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(IconSize.md - 4.dp),
        )
    }
}

/**
 * One row of a [SettingsGroup]: leading icon, title, optional subtitle, trailing control.
 *
 * Replaces the old `widgets/Tile`, which had no click support (so whole rows were dead space next
 * to their switch) and no disabled state (so Pro-locked rows looked identical to available ones).
 *
 * @param onClick makes the entire row tappable — always provide it when the trailing control is not
 *   itself the affordance (e.g. a row that opens a picker).
 * @param enabled dims the row's content; use for Pro-locked or unavailable settings.
 */
@Composable
fun SettingRow(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val contentAlpha = if (enabled) 1f else 0.45f
    val row = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        ) {
            RowIcon(
                id = icon,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    )
                }
            }
            trailing()
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            color = Color.Transparent,
            content = { row() },
        )
    } else {
        Box(modifier = modifier.fillMaxWidth()) { row() }
    }
}
