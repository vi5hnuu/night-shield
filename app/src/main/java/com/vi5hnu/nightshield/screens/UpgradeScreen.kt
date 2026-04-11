package com.vi5hnu.nightshield.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vi5hnu.nightshield.R

private data class ProFeature(val icon: String, val title: String, val description: String)

private val PRO_FEATURES = listOf(
    ProFeature("✦", "Smart Profiles",        "Save & switch filter presets with one tap"),
    ProFeature("🎨", "Per-app Custom Color", "Set a unique filter color for each app"),
    ProFeature("🌅", "Gradual Fade-in",      "Filter eases in gently — no jarring flash"),
    ProFeature("⏰", "Unlimited Schedules",  "Set different intensities at different times"),
    ProFeature("☀️", "Sunrise Alarm Mode",   "Filter gradually brightens at your wake time"),
    ProFeature("🔔", "Notification Controls","Adjust intensity +/−10% right from the notification"),
    ProFeature("🎛️", "Widget Customization", "Choose Standard, Minimal or Detailed widget"),
    ProFeature("🌙", "App Themes",           "6 themes: OLED, Warm, Blue Night, Forest, Purple Night & more"),
    ProFeature("💾", "Backup & Restore",     "Export & import all your settings"),
    ProFeature("⚡", "Tasker Integration",   "Automate with Tasker, Shortcuts & Bixby"),
    ProFeature("📊", "7-Day Blue Light Report","Weekly stats on your filter usage"),
)

private data class CompareRow(val feature: String, val free: String, val pro: String)
private val COMPARE_ROWS = listOf(
    CompareRow("Blue light filter",   "✓", "✓"),
    CompareRow("Temperature presets", "✓", "✓"),
    CompareRow("Schedule",            "1",  "Unlimited"),
    CompareRow("Per-app filter",      "✓", "✓ + custom color"),
    CompareRow("Profiles",            "—", "✓"),
    CompareRow("7-day report",        "—", "✓"),
    CompareRow("Gradual fade-in",     "—", "✓"),
    CompareRow("Notification ±10%",   "—", "✓"),
    CompareRow("Backup & Restore",    "—", "✓"),
    CompareRow("Tasker / Shortcuts",  "—", "✓"),
)

@Composable
fun UpgradeScreen(
    isPro: Boolean,
    onPurchase: () -> Unit,
    onRestorePurchase: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    painterResource(R.drawable.ic_close_24),
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        ) {
            // ── Header ────────────────────────────────────────────────────────
            item {
                // Gradient icon background
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                                )
                            )
                        )
                ) {
                    Icon(
                        painterResource(R.drawable.ic_moon_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(52.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    if (isPro) "You're Pro!" else "Night Shield Pro",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (isPro)
                        "All premium features are unlocked. Thank you for supporting Night Shield!"
                    else
                        "One payment · All features · Forever yours",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                if (!isPro) {
                    Spacer(Modifier.height(8.dp))
                    // Social proof
                    Text(
                        "⭐ Trusted by 10,000+ users · 4.8 rating",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(28.dp))
            }

            // ── Feature list ──────────────────────────────────────────────────
            if (!isPro) {
                items(PRO_FEATURES) { feature ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            feature.icon,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Column {
                            Text(
                                feature.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
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
            }

            // ── Comparison table ──────────────────────────────────────────────
            if (!isPro) {
                item {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Free vs Pro",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            // Header row
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("", modifier = Modifier.weight(2f))
                                Text(
                                    "Free",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    "Pro",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            COMPARE_ROWS.forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        row.feature,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(2f),
                                    )
                                    Text(
                                        row.free,
                                        style = MaterialTheme.typography.bodySmall,
                                        // "—" stays muted but readable; actual values get full weight
                                        color = if (row.free == "—")
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        else
                                            MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (row.free == "—") FontWeight.Normal else FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                    )
                                    Text(
                                        row.pro,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Price + buttons ───────────────────────────────────────────────
            item {
                Spacer(Modifier.height(32.dp))

                if (!isPro) {
                    // Sale/launch price banner
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            "🏷️ Launch price — grab it before it goes up",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            // "Best Value" badge
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primary,
                            ) {
                                Text(
                                    "Best Value · One-time, not a subscription",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "₹49",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                "Pay once · Unlock forever · No expiry",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = onPurchase,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(
                            "Upgrade Now — ₹49",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    TextButton(onClick = onRestorePurchase) {
                        Text(
                            "Restore Purchase",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("Back to App", style = MaterialTheme.typography.titleMedium)
                    }
                }

                Spacer(Modifier.navigationBarsPadding().height(16.dp))
            }
        }
    }
}
