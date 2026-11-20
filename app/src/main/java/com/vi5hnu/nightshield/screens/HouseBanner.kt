package com.vi5hnu.nightshield.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vi5hnu.nightshield.R
import com.vi5hnu.nightshield.ui.theme.IconSize
import com.vi5hnu.nightshield.ui.theme.Radius
import com.vi5hnu.nightshield.ui.theme.Spacing

/**
 * Night Shield's own Pro card, shown in the banner slot when no banner ad can be served (offline,
 * poor network, or the app's internet cut off). See [com.vi5hnu.nightshield.HouseAds] for why
 * these cases are treated alike.
 *
 * It takes exactly the ad's [height], so the layout doesn't jump when a real ad returns. The style
 * matches [com.vi5hnu.nightshield.ui.components.InfoBanner] (neutral surface, accent hairline)
 * so it reads as part of the app, not as a louder ad. The whole card is one tap target.
 *
 * @param onSeePro opens the paywall.
 */
@Composable
fun HouseBanner(height: Dp, onSeePro: () -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onSeePro,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(accent.copy(alpha = 0.16f), RoundedCornerShape(Radius.sm)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_star_24),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(IconSize.sm),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Enjoy Night Shield without ads",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "One payment, yours forever",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "See Pro",
                style = MaterialTheme.typography.labelLarge,
                color = accent,
            )
        }
    }
}
