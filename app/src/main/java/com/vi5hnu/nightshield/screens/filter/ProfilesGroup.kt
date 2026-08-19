package com.vi5hnu.nightshield.screens.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vi5hnu.nightshield.FilterProfile
import com.vi5hnu.nightshield.NightShieldManager
import com.vi5hnu.nightshield.R
import com.vi5hnu.nightshield.ui.components.ProBadge
import com.vi5hnu.nightshield.ui.components.SettingsDivider
import com.vi5hnu.nightshield.ui.components.SettingsGroup
import com.vi5hnu.nightshield.ui.theme.IconSize
import com.vi5hnu.nightshield.ui.theme.Radius
import com.vi5hnu.nightshield.ui.theme.Spacing

/** A ready-made colour + intensity combination offered to every user. */
private data class StarterProfile(
    val name: String,
    val description: String,
    val colorArgb: Int,
    val intensity: Float,
)

private val STARTER_PROFILES = listOf(
    StarterProfile("Work mode", "Cool blue · low intensity", 0x663ABDE0, 0.35f),
    StarterProfile("Bedtime", "Deep amber · high intensity", 0xCCFFA500.toInt(), 0.85f),
    StarterProfile("Reading", "Warm ivory · medium", 0xBBFFCC88.toInt(), 0.55f),
    StarterProfile("Movie night", "Crimson · cinematic", 0xCC8B0000.toInt(), 0.70f),
    StarterProfile("Sunrise", "Rose gold · gentle", 0xAAE91E63.toInt(), 0.45f),
)

/**
 * Starter templates plus the user's own saved profiles.
 *
 * Templates stay visible to free users so the value of Pro is concrete rather than described;
 * applying one and saving new profiles are the Pro parts.
 */
@Composable
fun ProfilesGroup(
    isPro: Boolean,
    onShowUpgrade: () -> Unit,
) {
    val profiles by NightShieldManager.profiles.collectAsState()
    val canvasColor by NightShieldManager.canvasColor.collectAsState()
    val intensity by NightShieldManager.filterIntensity.collectAsState()
    var showSaveDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }

    SettingsGroup {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Starter templates",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (isPro) "Tap apply to switch instantly"
                    else "Unlock to apply these and save your own",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isPro) {
                TextButton(onClick = { showSaveDialog = true }) {
                    Icon(
                        painterResource(R.drawable.ic_add_24),
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.sm + 2.dp),
                    )
                    Spacer(Modifier.size(Spacing.xs))
                    Text("Save current", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                ProBadge(onClick = onShowUpgrade)
            }
        }

        Spacer(Modifier.height(Spacing.md))

        STARTER_PROFILES.forEach { profile ->
            ProfileRow(
                swatch = Color(profile.colorArgb),
                name = profile.name,
                description = profile.description,
                intensity = profile.intensity,
                dimmed = !isPro,
                trailing = {
                    if (isPro) {
                        FilledTonalButton(
                            onClick = {
                                NightShieldManager.setCanvasColor(Color(profile.colorArgb))
                                NightShieldManager.setFilterIntensity(profile.intensity)
                            },
                            contentPadding = PaddingValues(
                                horizontal = Spacing.md,
                                vertical = 6.dp,
                            ),
                            modifier = Modifier.height(34.dp),
                        ) {
                            Text("Apply", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        ProBadge(onClick = onShowUpgrade)
                    }
                },
            )
        }

        if (isPro && profiles.isNotEmpty()) {
            SettingsDivider()
            Text(
                "Your profiles",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    top = Spacing.md,
                    bottom = Spacing.sm,
                ),
            )
            profiles.forEach { profile ->
                ProfileRow(
                    swatch = Color(profile.colorArgb).copy(alpha = profile.intensity),
                    name = profile.name,
                    description = "Intensity ${(profile.intensity * 100).toInt()}%",
                    intensity = profile.intensity,
                    showIntensityPill = false,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(
                                onClick = {
                                    NightShieldManager.setCanvasColor(Color(profile.colorArgb))
                                    NightShieldManager.setFilterIntensity(profile.intensity)
                                },
                                contentPadding = PaddingValues(
                                    horizontal = Spacing.md,
                                    vertical = 6.dp,
                                ),
                                modifier = Modifier.height(34.dp),
                            ) {
                                Text("Apply", style = MaterialTheme.typography.labelMedium)
                            }
                            IconButton(
                                onClick = { NightShieldManager.removeProfile(profile.id) },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_delete_24),
                                    contentDescription = "Delete ${profile.name}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(IconSize.sm + 2.dp),
                                )
                            }
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(Spacing.sm))
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false; newProfileName = "" },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Save profile", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        "Saves the current colour at ${(intensity * 100).toInt()}% intensity.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text("Profile name") },
                        singleLine = true,
                        shape = RoundedCornerShape(Radius.sm),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProfileName.isNotBlank()) {
                            NightShieldManager.addProfile(
                                FilterProfile(
                                    name = newProfileName.trim(),
                                    colorArgb = canvasColor.toArgb(),
                                    intensity = intensity,
                                ),
                            )
                            newProfileName = ""
                            showSaveDialog = false
                        }
                    },
                    shape = RoundedCornerShape(Radius.sm),
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSaveDialog = false; newProfileName = "" },
                ) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ProfileRow(
    swatch: Color,
    name: String,
    description: String,
    intensity: Float,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    showIntensityPill: Boolean = true,
    trailing: @Composable () -> Unit,
) {
    val alpha = if (dimmed) 0.55f else 1f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(swatch, RoundedCornerShape(Radius.sm)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        if (showIntensityPill) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    "${(intensity * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp),
                )
            }
        }
        trailing()
    }
}
