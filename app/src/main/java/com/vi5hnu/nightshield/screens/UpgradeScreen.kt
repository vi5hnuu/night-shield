package com.vi5hnu.nightshield.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vi5hnu.nightshield.BillingManager
import com.vi5hnu.nightshield.R
import com.vi5hnu.nightshield.ui.theme.IconSize
import com.vi5hnu.nightshield.ui.theme.Radius
import com.vi5hnu.nightshield.ui.theme.Spacing

/**
 * Shown while Play has not returned a localised price yet.
 *
 * Deliberately not a number: a hardcoded amount is wrong in every other currency and goes stale
 * the moment the price changes in Play Console (the previous "₹49" was already out of date).
 */
private const val PRICE_PENDING = "One-time purchase"

private data class ProFeature(
    @param:DrawableRes val icon: Int,
    val title: String,
    val description: String,
)

private val PRO_FEATURES = listOf(
    ProFeature(R.drawable.ic_star_24, "Smart profiles", "Save and switch filter presets with one tap"),
    ProFeature(R.drawable.ic_palette_24, "Per-app colour", "Give every app its own filter colour"),
    ProFeature(R.drawable.ic_twilight_24, "Gradual fade-in", "The filter eases in instead of snapping on"),
    ProFeature(R.drawable.ic_alarm_24, "Unlimited schedules", "Different intensities at different times"),
    ProFeature(R.drawable.ic_light_mode_24, "Sunrise mode", "The filter lifts gradually at your wake-up time"),
    ProFeature(R.drawable.ic_notifications_24, "Notification controls", "Nudge intensity ±10% from the notification"),
    ProFeature(R.drawable.ic_widgets_24, "Widget styles", "Standard, minimal or detailed home-screen widgets"),
    ProFeature(R.drawable.ic_dark_mode_24, "App themes", "Six palettes including OLED black and Material You"),
    ProFeature(R.drawable.ic_download_24, "Backup & restore", "Export and import every setting"),
    ProFeature(R.drawable.ic_bolt_24, "Automation", "Drive the filter from Tasker, Shortcuts or Bixby"),
    ProFeature(R.drawable.ic_bar_chart_24, "7-day report", "Weekly stats on your filtered screen time"),
)

private data class CompareRow(val feature: String, val free: String, val pro: String)

private val COMPARE_ROWS = listOf(
    CompareRow("Blue light filter", "Yes", "Yes"),
    CompareRow("Temperature presets", "Yes", "Yes"),
    CompareRow("Schedules", "1", "Unlimited"),
    CompareRow("Per-app filter", "3 apps", "Unlimited + colour"),
    CompareRow("Profiles", "—", "Yes"),
    CompareRow("7-day report", "—", "Yes"),
    CompareRow("Gradual fade-in", "—", "Yes"),
    CompareRow("Notification ±10%", "—", "Yes"),
    CompareRow("Backup & restore", "—", "Yes"),
    CompareRow("Tasker & shortcuts", "—", "Yes"),
)

/**
 * The paywall.
 *
 * The buy button is pinned to the bottom rather than sitting at the end of a long scroll, so the
 * decision is always one tap away, and the feature list is icon-led instead of the emoji column it
 * used to be.
 */
@Composable
fun UpgradeScreen(
    isPro: Boolean,
    onPurchase: () -> Unit,
    onRestorePurchase: () -> Unit,
    onDismiss: () -> Unit,
) {
    val storePrice by BillingManager.formattedPrice.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    painterResource(R.drawable.ic_close_24),
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.md),
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.sm),
        ) {
            item { Header(isPro = isPro) }

            if (!isPro) {
                items(PRO_FEATURES) { feature -> FeatureRow(feature) }

                item {
                    Spacer(Modifier.height(Spacing.xxl))
                    ComparisonTable()
                    Spacer(Modifier.height(Spacing.xxl))
                    PriceCard(price = storePrice)
                    Spacer(Modifier.height(Spacing.lg))
                }
            } else {
                item {
                    Spacer(Modifier.height(Spacing.xxl))
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(Radius.md),
                    ) { Text("Back to the app", style = MaterialTheme.typography.labelLarge) }
                }
            }
        }

        if (!isPro) {
            // Pinned call to action.
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = Spacing.xl, vertical = Spacing.md)
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(
                        onClick = onPurchase,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(Radius.md),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(
                            text = storePrice?.let { "Unlock Pro · $it" } ?: "Unlock Pro",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    TextButton(onClick = onRestorePurchase) {
                        Text(
                            "Restore purchase",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(isPro: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ),
                ),
        ) {
            Icon(
                painterResource(R.drawable.ic_premium_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(IconSize.xl),
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(
            if (isPro) "You're Pro" else "Night Shield Pro",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            if (isPro) {
                "Every premium feature is unlocked. Thank you for supporting Night Shield."
            } else {
                "One payment. Every feature. No subscription."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xxl))
    }
}

@Composable
private fun FeatureRow(feature: ProFeature) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(feature.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSize.md - 4.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                feature.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                feature.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ComparisonTable() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
                "Free vs Pro",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.md))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Spacer(Modifier.weight(2f))
                Text(
                    "Free",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Pro",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            COMPARE_ROWS.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        row.feature,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(2f),
                    )
                    CompareCell(value = row.free, modifier = Modifier.weight(1f), emphasised = false)
                    CompareCell(value = row.pro, modifier = Modifier.weight(1f), emphasised = true)
                }
            }
        }
    }
}

/** Renders "Yes" as a tick so the table scans as a column of marks rather than repeated words. */
@Composable
private fun CompareCell(value: String, modifier: Modifier, emphasised: Boolean) {
    val color = when {
        value == "—" -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        emphasised -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (value == "Yes") {
            Icon(
                painterResource(R.drawable.ic_check_24),
                contentDescription = "Included",
                tint = color,
                modifier = Modifier.size(IconSize.sm + 2.dp),
            )
        } else {
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = color,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PriceCard(price: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = price ?: PRICE_PENDING,
                style = if (price != null) MaterialTheme.typography.displaySmall
                else MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Pay once · Unlock forever · No expiry",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}
