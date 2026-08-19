package com.vi5hnu.nightshield.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vi5hnu.nightshield.BuildConfig
import com.vi5hnu.nightshield.NightShieldManager
import com.vi5hnu.nightshield.R
import com.vi5hnu.nightshield.screens.NightShieldActions
import com.vi5hnu.nightshield.ui.components.ProBadge
import com.vi5hnu.nightshield.ui.components.SectionHeader
import com.vi5hnu.nightshield.ui.components.SettingRow
import com.vi5hnu.nightshield.ui.components.SettingsDivider
import com.vi5hnu.nightshield.ui.components.SettingsGroup
import com.vi5hnu.nightshield.ui.theme.IconSize
import com.vi5hnu.nightshield.ui.theme.Radius
import com.vi5hnu.nightshield.ui.theme.Spacing
import com.vi5hnu.nightshield.ui.theme.paletteFor

/**
 * Configuration that is not about the filter itself: how the app and its widgets look, where the
 * settings live, and how other apps can drive Night Shield.
 */
@Composable
fun SettingsScreen(
    isPro: Boolean,
    actions: NightShieldActions,
    onShowUpgrade: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
    ) {
        Spacer(Modifier.height(Spacing.sm))

        if (!isPro) {
            UpgradeCard(onShowUpgrade = onShowUpgrade)
            Spacer(Modifier.height(Spacing.xxl))
        }

        // ── Appearance ────────────────────────────────────────────────────────
        SectionHeader("Appearance")
        ThemePicker(isPro = isPro, onShowUpgrade = onShowUpgrade)

        Spacer(Modifier.height(Spacing.lg))

        SettingsGroup {
            val currentStyle by NightShieldManager.widgetStyle.collectAsState()
            // The lock lives on the group header: repeating a badge on each of the three rows made
            // the paywall shout three times for one decision.
            GroupHeader(
                title = "Home screen widget",
                subtitle = if (isPro) "Choose how the widget looks" else "Pick a widget layout with Pro",
                trailing = { if (!isPro) ProBadge(onClick = onShowUpgrade) },
            )
            NightShieldManager.WidgetStyle.entries.forEachIndexed { index, style ->
                SettingRow(
                    icon = R.drawable.ic_widgets_24,
                    title = style.label,
                    subtitle = when (style) {
                        NightShieldManager.WidgetStyle.STANDARD -> "Icon with a toggle button"
                        NightShieldManager.WidgetStyle.MINIMAL -> "Icon only — the smallest footprint"
                        NightShieldManager.WidgetStyle.DETAILED -> "Icon with status and intensity"
                    },
                    enabled = isPro,
                    onClick = if (isPro) {
                        { NightShieldManager.setWidgetStyle(style) }
                    } else {
                        onShowUpgrade
                    },
                    trailing = { if (isPro) SelectionMark(selected = currentStyle == style) },
                )
                if (index < NightShieldManager.WidgetStyle.entries.lastIndex) SettingsDivider()
            }
        }

        Spacer(Modifier.height(Spacing.xxl))

        // ── Data ──────────────────────────────────────────────────────────────
        SectionHeader("Backup")
        SettingsGroup {
            SettingRow(
                icon = R.drawable.ic_upload_24,
                title = "Export settings",
                subtitle = "Save every setting to a JSON file",
                enabled = isPro,
                onClick = if (isPro) actions.onExportSettings else onShowUpgrade,
                trailing = {
                    if (isPro) {
                        FilledTonalButton(
                            onClick = actions.onExportSettings,
                            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = 6.dp),
                            modifier = Modifier.height(34.dp),
                        ) { Text("Export", style = MaterialTheme.typography.labelMedium) }
                    } else {
                        ProBadge(onClick = onShowUpgrade)
                    }
                },
            )
            SettingsDivider()
            SettingRow(
                icon = R.drawable.ic_download_24,
                title = "Import settings",
                subtitle = "Restore from a backup file",
                enabled = isPro,
                onClick = if (isPro) actions.onImportSettings else onShowUpgrade,
                trailing = {
                    if (isPro) {
                        FilledTonalButton(
                            onClick = actions.onImportSettings,
                            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = 6.dp),
                            modifier = Modifier.height(34.dp),
                        ) { Text("Import", style = MaterialTheme.typography.labelMedium) }
                    } else {
                        ProBadge(onClick = onShowUpgrade)
                    }
                },
            )
        }

        Spacer(Modifier.height(Spacing.xxl))

        // ── Automation ────────────────────────────────────────────────────────
        SectionHeader("Automation")
        TaskerGroup(isPro = isPro, onShowUpgrade = onShowUpgrade)

        Spacer(Modifier.height(Spacing.xxl))

        // ── About ─────────────────────────────────────────────────────────────
        SectionHeader("About")
        SettingsGroup {
            SettingRow(
                icon = R.drawable.ic_shield_24,
                title = stringResource(R.string.app_name),
                subtitle = "Version ${BuildConfig.VERSION_NAME}",
            )
        }

        Spacer(Modifier.height(Spacing.lg))

        Text(
            stringResource(R.string.footer_text),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Spacing.xxxl))
    }
}

// ── Upgrade entry point ───────────────────────────────────────────────────────

@Composable
private fun UpgradeCard(onShowUpgrade: () -> Unit) {
    Surface(
        onClick = onShowUpgrade,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0f),
                        ),
                    ),
                )
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_premium_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(IconSize.lg - 4.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Night Shield Pro",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Profiles, themes, automation and more — one payment",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painterResource(R.drawable.ic_chevron_right_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.md),
            )
        }
    }
}

// ── Theme picker ──────────────────────────────────────────────────────────────

/**
 * Themes as swatches rather than a stack of radio rows: colour is the thing being chosen, so it
 * should be the thing on screen.
 */
@Composable
private fun ThemePicker(isPro: Boolean, onShowUpgrade: () -> Unit) {
    val currentTheme by NightShieldManager.appTheme.collectAsState()

    SettingsGroup {
        GroupHeader(
            title = "App theme",
            subtitle = currentTheme.label,
            trailing = { if (!isPro) ProBadge(onClick = onShowUpgrade) },
        )
        Column(modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                NightShieldManager.AppTheme.entries.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        row.forEach { theme ->
                            // Only the default theme is free; the rest are part of Pro.
                            val unlocked = isPro || theme == NightShieldManager.AppTheme.SYSTEM
                            ThemeSwatch(
                                theme = theme,
                                selected = currentTheme == theme,
                                unlocked = unlocked,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (unlocked) NightShieldManager.setAppTheme(theme)
                                    else onShowUpgrade()
                                },
                            )
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/**
 * One theme option, drawn as a miniature of the screen it produces: the theme's own background, a
 * card band and its accent. A locked theme keeps its colours visible — hiding them behind a scrim
 * would defeat the point of showing a swatch — and carries a small lock in the corner instead.
 */
@Composable
private fun ThemeSwatch(
    theme: NightShieldManager.AppTheme,
    selected: Boolean,
    unlocked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = paletteFor(theme)
    // Material You has no static palette — mirror the scheme currently in effect instead.
    val background = palette?.background ?: MaterialTheme.colorScheme.background
    val accent = palette?.primary ?: MaterialTheme.colorScheme.primary
    val card = palette?.surfaceContainerHigh ?: MaterialTheme.colorScheme.surfaceContainerHigh

    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(background)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(Radius.sm),
                ),
        ) {
            // Miniature of a screen: accent pill over a card band.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(accent),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(card),
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.75f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(card),
                )
            }
            if (!unlocked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_lock_24),
                        contentDescription = "Requires Pro",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_check_24),
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            theme.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** Title row for a group whose rows share one setting or one paywall. */
@Composable
private fun GroupHeader(
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}

@Composable
private fun SelectionMark(selected: Boolean) {
    if (!selected) return
    Icon(
        painterResource(R.drawable.ic_check_24),
        contentDescription = "Selected",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(IconSize.md),
    )
}

// ── Automation ────────────────────────────────────────────────────────────────

@Composable
private fun TaskerGroup(isPro: Boolean, onShowUpgrade: () -> Unit) {
    SettingsGroup {
        SettingRow(
            icon = R.drawable.ic_bolt_24,
            title = "Tasker & shortcuts",
            subtitle = "Drive Night Shield from other apps with broadcast intents",
            enabled = isPro,
            onClick = if (isPro) null else onShowUpgrade,
            trailing = { if (!isPro) ProBadge(onClick = onShowUpgrade) },
        )
        if (isPro) {
            SettingsDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    "Send these broadcasts from Tasker, Shortcuts or adb:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf(
                    "ACTION_FILTER_ON" to "Turn the filter on",
                    "ACTION_FILTER_OFF" to "Turn the filter off",
                    "ACTION_FILTER_TOGGLE" to "Toggle the filter",
                ).forEach { (action, description) ->
                    Column {
                        Text(
                            "com.vi5hnu.nightshield.$action",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "Optional extra: intensity (float, 0.1–1.0)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
