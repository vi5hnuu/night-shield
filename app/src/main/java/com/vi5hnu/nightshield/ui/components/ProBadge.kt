package com.vi5hnu.nightshield.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import com.vi5hnu.nightshield.R
import com.vi5hnu.nightshield.ui.theme.IconSize
import com.vi5hnu.nightshield.ui.theme.Spacing

/**
 * The single affordance for "this needs Pro".
 *
 * One consistent badge everywhere — previously some places showed a moon icon labelled PRO, others
 * an inline lock, others nothing at all, so the paywall read as random rather than as one boundary.
 * Tapping it always opens the upgrade screen.
 */
@Composable
fun ProBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lock_24),
                contentDescription = null,
                modifier = Modifier.size(IconSize.sm - 2.dp),
            )
            Text(
                text = "PRO",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
