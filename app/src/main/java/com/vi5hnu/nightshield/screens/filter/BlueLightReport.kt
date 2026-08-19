package com.vi5hnu.nightshield.screens.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vi5hnu.nightshield.UsageTracker
import com.vi5hnu.nightshield.ui.components.ProBadge
import com.vi5hnu.nightshield.ui.components.SettingsGroup
import com.vi5hnu.nightshield.ui.theme.Spacing

/**
 * Seven-day filter-usage chart.
 *
 * Free users see today's real bar with the rest of the week masked, so the feature is visibly
 * present rather than merely advertised.
 */
@Composable
fun BlueLightReport(
    isPro: Boolean,
    onShowUpgrade: () -> Unit,
) {
    val context = LocalContext.current
    val usage = remember { UsageTracker.getWeeklyUsage(context) }
    val maxMinutes = usage.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1

    SettingsGroup {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            if (isPro) {
                val totalHours = usage.sumOf { it.second } / 60f
                Text(
                    "%.1f hours of filtered screen time".format(totalHours),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.lg))
            }

            Box {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    usage.forEachIndexed { index, (date, minutes) ->
                        // Free users only see real data for today (the last entry).
                        val isRevealed = isPro || index == usage.lastIndex
                        val displayMinutes = if (isRevealed) minutes else 30
                        val barFraction = displayMinutes.toFloat() / maxMinutes.coerceAtLeast(30)
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((barFraction * 72).dp.coerceAtLeast(3.dp))
                                    .background(
                                        if (isRevealed) {
                                            MaterialTheme.colorScheme.primary
                                                .copy(alpha = 0.65f + 0.35f * barFraction)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                                        },
                                        RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                                    ),
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                text = date.takeLast(5),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = if (isRevealed) 1f else 0.35f),
                                maxLines = 1,
                            )
                        }
                    }
                }

                if (!isPro) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    0f to MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0f),
                                    0.2f to MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Unlock your 7-day history",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            ProBadge(onClick = onShowUpgrade)
                        }
                    }
                }
            }
        }
    }
}
