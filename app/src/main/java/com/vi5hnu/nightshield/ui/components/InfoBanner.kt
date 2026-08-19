package com.vi5hnu.nightshield.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vi5hnu.nightshield.R
import com.vi5hnu.nightshield.ui.theme.IconSize
import com.vi5hnu.nightshield.ui.theme.Radius
import com.vi5hnu.nightshield.ui.theme.Spacing

/**
 * An inline notice: a hint, a warning, or a call to fix something.
 *
 * Replaces the ad-hoc emoji notes (`ℹ️ …`) and the bespoke battery card, so every advisory in the
 * app looks the same and carries its actions in the same place.
 *
 * @param actions optional trailing buttons; use [InfoBannerAction] entries.
 */
@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    @DrawableRes icon: Int = R.drawable.ic_info_24,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    actions: List<InfoBannerAction> = emptyList(),
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        color = container,
        contentColor = content,
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.md - 4.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    if (title != null) {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = 0.85f),
                    )
                }
            }
            if (actions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End),
                ) {
                    actions.forEach { action ->
                        TextButton(
                            onClick = action.onClick,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = Spacing.md,
                                vertical = 6.dp,
                            ),
                        ) {
                            Text(
                                action.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (action.emphasised) content else content.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A button shown at the bottom of an [InfoBanner]. */
data class InfoBannerAction(
    val label: String,
    val emphasised: Boolean = false,
    val onClick: () -> Unit,
)
