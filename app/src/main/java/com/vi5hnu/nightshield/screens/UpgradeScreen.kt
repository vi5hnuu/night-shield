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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vi5hnu.nightshield.R

private data class ProFeature(val title: String, val description: String)

private val PRO_FEATURES = listOf(
    ProFeature("Smart Profiles",          "Save & switch filter presets with one tap"),
    ProFeature("Per-app Custom Color",    "Set a unique filter color for each app"),
    ProFeature("Gradual Fade-in",         "Filter eases in gently — no jarring flash"),
    ProFeature("Intensity Scheduling",    "Set different intensities at different times"),
    ProFeature("Sunrise Alarm Mode",      "Filter gradually brightens at your wake time"),
    ProFeature("Widget Customization",    "Choose the style of your home screen widget"),
    ProFeature("App Themes",              "Dark OLED, Warm tones & more"),
    ProFeature("Backup & Restore",        "Export & import all your settings"),
    ProFeature("Tasker Integration",      "Automate with Tasker, Shortcuts & Bixby"),
    ProFeature("Blue Light Report",       "Weekly stats on your filter usage"),
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
                    painterResource(R.drawable.ic_delete_24),
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
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
                Icon(
                    painterResource(R.drawable.ic_moon_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    if (isPro) "You're Pro!" else "Night Shield Pro",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isPro)
                        "All premium features are unlocked. Thank you for supporting Night Shield!"
                    else
                        "One payment · All features · Forever yours",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
            }

            // ── Feature list ──────────────────────────────────────────────────
            items(PRO_FEATURES) { feature ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
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

            // ── Price + buttons ───────────────────────────────────────────────
            item {
                Spacer(Modifier.height(32.dp))

                if (!isPro) {
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
                            Text(
                                "₹49",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                "One-time purchase · No subscription · No expiry",
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
