package com.vi5hnu.nightshield.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
 * Styled as a calm card — neutral surface, a hairline in the [accent] colour and the icon in an
 * accent-tinted tile — rather than a fully filled colour block. The filled version shouted on every
 * launch (the battery notice sat above the main control as a solid mustard slab) and competed with
 * the primary action for attention.
 *
 * @param accent colour of the icon tile, outline and emphasised action.
 * @param actions optional trailing buttons; use [InfoBannerAction] entries.
 */
@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    @DrawableRes icon: Int = R.drawable.ic_info_24,
    accent: Color = MaterialTheme.colorScheme.primary,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurface,
    actions: List<InfoBannerAction> = emptyList(),
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
    ) {
        Column(
            modifier = Modifier.padding(
                start = Spacing.md,
                end = Spacing.md,
                top = Spacing.md,
                // A trailing action row brings its own button padding, so the banner needs less.
                bottom = if (actions.isEmpty()) Spacing.md else Spacing.xs,
            ),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(accent.copy(alpha = 0.16f), RoundedCornerShape(Radius.md)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(IconSize.sm + 2.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    if (title != null) {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (actions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                                color = if (action.emphasised) accent
                                else MaterialTheme.colorScheme.onSurfaceVariant,
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
