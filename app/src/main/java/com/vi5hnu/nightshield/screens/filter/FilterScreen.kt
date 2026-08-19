package com.vi5hnu.nightshield.screens.filter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vi5hnu.nightshield.BatteryHelpers
import com.vi5hnu.nightshield.BedtimeHelper
import com.vi5hnu.nightshield.IntensityWidgetProvider
import com.vi5hnu.nightshield.NightShieldController
import com.vi5hnu.nightshield.NightShieldManager
import com.vi5hnu.nightshield.OverlayHelpers
import com.vi5hnu.nightshield.R
import com.vi5hnu.nightshield.screens.NightShieldActions
import com.vi5hnu.nightshield.ui.components.InfoBanner
import com.vi5hnu.nightshield.ui.components.InfoBannerAction
import com.vi5hnu.nightshield.ui.components.ProBadge
import com.vi5hnu.nightshield.ui.components.SectionHeader
import com.vi5hnu.nightshield.ui.components.SettingRow
import com.vi5hnu.nightshield.ui.components.SettingsDivider
import com.vi5hnu.nightshield.ui.components.SettingsGroup
import com.vi5hnu.nightshield.ui.components.SegmentedChips
import com.vi5hnu.nightshield.ui.components.SliderRow
import com.vi5hnu.nightshield.ui.theme.IconSize
import com.vi5hnu.nightshield.ui.theme.Radius
import com.vi5hnu.nightshield.ui.theme.Spacing
import com.vi5hnu.nightshield.widgets.ColorDot
import com.vi5hnu.nightshield.widgets.ColorPicker

/**
 * The app's home tab: the filter's current state, the one primary action, and every control that
 * shapes how the filter looks while it runs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterScreen(
    hasOverlayPermission: Boolean,
    areServicesActive: Boolean,
    allowShake: Boolean,
    isPro: Boolean,
    actions: NightShieldActions,
    onShowUpgrade: () -> Unit,
) {
    val context = LocalContext.current

    // Re-evaluated on every lifecycle state change so values reflect the latest state when the
    // user returns from Settings (e.g. after granting the battery-optimisation exemption).
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val streakDays = remember(lifecycleState) { OverlayHelpers.getStreakDays(context) }

    val canvasColor by NightShieldManager.canvasColor.collectAsState()
    var showColorSheet by remember { mutableStateOf(false) }
    // Snapshot the colour before opening the sheet so dismissing without Apply can revert.
    var colorBeforeSheet by remember { mutableStateOf(canvasColor) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
    ) {
        Spacer(Modifier.height(Spacing.sm))

        BatteryOptBanner(lifecycleKey = lifecycleState)

        FilterHero(
            hasOverlayPermission = hasOverlayPermission,
            areServicesActive = areServicesActive,
            streakDays = streakDays,
            onPermissionRequest = actions.onPermissionRequest,
            onActivate = actions.launchOverlays,
            onStop = actions.stopOverlays,
        )

        Spacer(Modifier.height(Spacing.xxl))

        // ── Quick presets ─────────────────────────────────────────────────────
        SectionHeader(stringResource(R.string.temperature_title))
        TemperaturePresetGrid()

        Spacer(Modifier.height(Spacing.xxl))

        // ── Appearance of the filter itself ───────────────────────────────────
        SectionHeader("Filter")
        SettingsGroup {
            SettingRow(
                icon = R.drawable.ic_palette_24,
                title = stringResource(R.string.filter_color_title),
                subtitle = stringResource(R.string.filter_color_subtitle),
                onClick = {
                    colorBeforeSheet = canvasColor
                    showColorSheet = true
                },
                trailing = {
                    ColorDot(color = canvasColor, sizeDp = 32.dp) {
                        colorBeforeSheet = canvasColor
                        showColorSheet = true
                    }
                },
            )
            SettingsDivider()

            // Intensity — persisted on drag end so a service restart reads the committed value.
            val intensity by NightShieldManager.filterIntensity.collectAsState()
            SliderRow(
                icon = R.drawable.ic_brightness_24,
                title = stringResource(R.string.filter_intensity_title),
                subtitle = stringResource(R.string.filter_intensity_subtitle),
                value = intensity,
                valueRange = 0.1f..1.0f,
                valueLabel = "${(intensity * 100).toInt()}%",
                onValueChange = { NightShieldManager.setFilterIntensity(it) },
                onValueChangeFinished = {
                    OverlayHelpers.saveFilterSettings(
                        context,
                        NightShieldManager.canvasColor.value,
                        NightShieldManager.filterIntensity.value,
                        NightShieldManager.allowShake.value,
                    )
                    IntensityWidgetProvider.updateAll(context)
                },
            )
            SettingsDivider()

            val dimLevel by NightShieldManager.dimLevel.collectAsState()
            SliderRow(
                icon = R.drawable.ic_light_mode_24,
                title = "Screen dimming",
                subtitle = "Darken the screen beyond the system minimum",
                value = dimLevel,
                valueRange = 0f..0.85f,
                valueLabel = if (dimLevel <= 0f) "Off" else "${(dimLevel * 100).toInt()}%",
                onValueChange = { NightShieldManager.setDimLevel(it) },
                onValueChangeFinished = {
                    OverlayHelpers.saveDimLevel(context, NightShieldManager.dimLevel.value)
                },
            )
            SettingsDivider()

            val gradualFade by NightShieldManager.gradualFadeEnabled.collectAsState()
            SettingRow(
                icon = R.drawable.ic_twilight_24,
                title = "Gradual fade-in",
                subtitle = "Filter eases in over 12 s instead of snapping on",
                enabled = isPro,
                onClick = if (isPro) null else onShowUpgrade,
                trailing = {
                    if (isPro) {
                        Switch(
                            checked = gradualFade,
                            onCheckedChange = {
                                NightShieldManager.setGradualFadeEnabled(it)
                                OverlayHelpers.saveGradualFade(context, it)
                            },
                        )
                    } else {
                        ProBadge(onClick = onShowUpgrade)
                    }
                },
            )
            SettingsDivider()

            val adaptive by NightShieldManager.adaptiveIntensity.collectAsState()
            SettingRow(
                icon = R.drawable.ic_tune_24,
                title = "Adaptive intensity",
                subtitle = "Eases the filter using the light sensor — your setting stays the maximum",
                enabled = isPro,
                onClick = if (isPro) null else onShowUpgrade,
                trailing = {
                    if (isPro) {
                        Switch(
                            checked = adaptive,
                            onCheckedChange = {
                                NightShieldManager.setAdaptiveIntensity(it)
                                OverlayHelpers.saveAdaptiveIntensity(context, it)
                            },
                        )
                    } else {
                        ProBadge(onClick = onShowUpgrade)
                    }
                },
            )
        }

        Spacer(Modifier.height(Spacing.xxl))

        // ── Shake to toggle ───────────────────────────────────────────────────
        SectionHeader("Shake")
        ShakeGroup(allowShake = allowShake, onAllowShake = actions.onAllowShake)

        Spacer(Modifier.height(Spacing.xxl))

        // ── Sleep timer ───────────────────────────────────────────────────────
        SectionHeader("Timer")
        SleepTimerGroup()

        Spacer(Modifier.height(Spacing.xxl))

        // ── Profiles ──────────────────────────────────────────────────────────
        SectionHeader("Profiles")
        ProfilesGroup(isPro = isPro, onShowUpgrade = onShowUpgrade)

        Spacer(Modifier.height(Spacing.xxl))

        // ── Weekly usage ──────────────────────────────────────────────────────
        SectionHeader("This week")
        BlueLightReport(isPro = isPro, onShowUpgrade = onShowUpgrade)

        Spacer(Modifier.height(Spacing.xxxl))
    }

    // Colour picker sheet — dragging previews live on the overlay; Apply commits; dismissing
    // without Apply reverts to the colour captured when the sheet opened.
    if (showColorSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                NightShieldManager.setCanvasColor(colorBeforeSheet)
                showColorSheet = false
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            dragHandle = null,
        ) {
            ColorPicker(
                initialColor = colorBeforeSheet,
                onChange = { NightShieldManager.setCanvasColor(it) },
                onDismiss = { showColorSheet = false },
            )
        }
    }
}

// ── Hero ──────────────────────────────────────────────────────────────────────

/**
 * Status and the single primary action.
 *
 * Everything a glance should answer — is the filter on, at what strength and colour, how long the
 * streak is — sits above the fold, with exactly one filled button so the main action is never
 * ambiguous.
 */
@Composable
private fun FilterHero(
    hasOverlayPermission: Boolean,
    areServicesActive: Boolean,
    streakDays: Int,
    onPermissionRequest: () -> Unit,
    onActivate: () -> Unit,
    onStop: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val canvasColor by NightShieldManager.canvasColor.collectAsState()
    val intensity by NightShieldManager.filterIntensity.collectAsState()

    val transition = rememberInfiniteTransition(label = "hero")
    val glowAlpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            tween(1400, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "glow",
    )
    val moonSpin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        label = "spin",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (areServicesActive) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.xl),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box(
            modifier = Modifier.background(
                if (areServicesActive) {
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0f),
                        ),
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainer,
                            MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    )
                },
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl, vertical = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Status ring
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                    if (areServicesActive) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha * 0.10f),
                                    CircleShape,
                                ),
                        )
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha * 0.16f),
                                    CircleShape,
                                ),
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(80.dp)
                            .scale(iconScale)
                            .clip(CircleShape)
                            .background(
                                if (areServicesActive) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_moon_24),
                            contentDescription = stringResource(
                                if (areServicesActive) R.string.status_active
                                else R.string.status_inactive,
                            ),
                            tint = if (areServicesActive) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(IconSize.xl)
                                .rotate(if (areServicesActive) moonSpin else 0f),
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.lg))

                Text(
                    text = stringResource(
                        if (areServicesActive) R.string.status_active else R.string.status_inactive,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (areServicesActive) {
                    Spacer(Modifier.height(Spacing.xs))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(canvasColor, CircleShape),
                        )
                        Text(
                            text = "${(intensity * 100).toInt()}% intensity",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (streakDays >= 1) {
                    Spacer(Modifier.height(Spacing.md))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = Spacing.md,
                                vertical = 6.dp,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_bolt_24),
                                contentDescription = null,
                                modifier = Modifier.size(IconSize.sm),
                            )
                            Text(
                                text = if (streakDays == 1) "1 day streak" else "$streakDays day streak",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.xl))

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        when {
                            !hasOverlayPermission -> onPermissionRequest()
                            areServicesActive -> onStop()
                            else -> onActivate()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(Radius.md),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            areServicesActive -> MaterialTheme.colorScheme.surfaceContainerHighest
                            else -> MaterialTheme.colorScheme.primary
                        },
                        contentColor = when {
                            areServicesActive -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onPrimary
                        },
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_power_24),
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.md - 4.dp),
                    )
                    Spacer(Modifier.size(Spacing.sm))
                    Text(
                        text = stringResource(
                            when {
                                !hasOverlayPermission -> R.string.btn_request_permission
                                areServicesActive -> R.string.btn_stop
                                else -> R.string.btn_activate
                            },
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                // One-tap bedtime routine — warm colour, dimmed, 30-minute sleep timer, then on.
                if (hasOverlayPermission && !areServicesActive) {
                    Spacer(Modifier.height(Spacing.md))
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            BedtimeHelper.apply(context)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(Radius.md),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_bedtime_24),
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.sm + 2.dp),
                        )
                        Spacer(Modifier.size(Spacing.sm))
                        Text("Bedtime — warm, dim, 30 min", style = MaterialTheme.typography.labelLarge)
                    }
                }

                if (!hasOverlayPermission) {
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        text = stringResource(R.string.permission_required_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ── Battery optimisation ──────────────────────────────────────────────────────

/**
 * @param lifecycleKey re-runs the exemption check whenever the lifecycle state changes, so the
 *   banner disappears as soon as the user comes back from granting it.
 */
@Composable
private fun BatteryOptBanner(lifecycleKey: Any) {
    val context = LocalContext.current
    var dismissed by remember { mutableStateOf(OverlayHelpers.isBatteryBannerDismissed(context)) }
    val isExempt = remember(lifecycleKey) {
        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    AnimatedVisibility(
        visible = !dismissed && !isExempt,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        Column {
            InfoBanner(
                title = stringResource(R.string.battery_opt_title),
                text = stringResource(R.string.battery_opt_body),
                icon = R.drawable.ic_bolt_24,
                actions = listOf(
                    InfoBannerAction(stringResource(R.string.battery_opt_dismiss)) {
                        dismissed = true
                        OverlayHelpers.saveBatteryBannerDismissed(context)
                    },
                    InfoBannerAction("Auto-start") { BatteryHelpers.openAutoStartSettings(context) },
                    InfoBannerAction(stringResource(R.string.battery_opt_fix), emphasised = true) {
                        BatteryHelpers.requestIgnoreBatteryOptimizations(context)
                    },
                ),
            )
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

// ── Temperature presets ───────────────────────────────────────────────────────

@Composable
private fun TemperaturePresetGrid() {
    val activePreset by NightShieldManager.activePreset.collectAsState()
    val presets = NightShieldManager.TemperaturePreset.entries

    SettingsGroup {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
                text = stringResource(R.string.temperature_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                presets.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        row.forEach { preset ->
                            PresetChip(
                                preset = preset,
                                isSelected = activePreset == preset,
                                modifier = Modifier.weight(1f),
                                onClick = { NightShieldManager.applyTemperaturePreset(preset) },
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    preset: NightShieldManager.TemperaturePreset,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.sm),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(vertical = Spacing.md, horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(preset.dotColor, CircleShape),
            )
            Text(
                text = preset.label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

// ── Shake ─────────────────────────────────────────────────────────────────────

@Composable
private fun ShakeGroup(
    allowShake: Boolean,
    onAllowShake: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

    SettingsGroup {
        SettingRow(
            icon = R.drawable.ic_vibration_24,
            title = stringResource(R.string.shake_toggle_title),
            subtitle = stringResource(R.string.shake_toggle_subtitle),
            trailing = { Switch(checked = allowShake, onCheckedChange = onAllowShake) },
        )

        AnimatedVisibility(
            visible = allowShake,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                SettingsDivider()

                val backgroundShake by NightShieldManager.backgroundShake.collectAsState()
                SettingRow(
                    icon = R.drawable.ic_notifications_24,
                    title = "Background shake",
                    subtitle = "Detect shakes while the app is closed. Off saves battery and hides the ongoing notification",
                    trailing = {
                        Switch(
                            checked = backgroundShake,
                            onCheckedChange = {
                                NightShieldManager.setBackgroundShake(it)
                                OverlayHelpers.saveBackgroundShake(context, it)
                                NightShieldController.syncShakeMonitor(context)
                            },
                        )
                    },
                )
                SettingsDivider()

                val shakeIntensity by NightShieldManager.shakeIntensity.collectAsState()
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    Text(
                        "Sensitivity",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        when (shakeIntensity) {
                            NightShieldManager.ShakeIntensity.GENTLE -> "A light shake triggers the filter"
                            NightShieldManager.ShakeIntensity.NORMAL -> "A moderate shake is required"
                            NightShieldManager.ShakeIntensity.FIRM -> "Only a strong, deliberate shake counts"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    SegmentedChips(
                        options = NightShieldManager.ShakeIntensity.entries,
                        selected = shakeIntensity,
                        label = { it.label },
                        onSelect = {
                            NightShieldManager.setShakeIntensity(it)
                            OverlayHelpers.saveShakeIntensity(context, it)
                        },
                    )
                }

                // Turning the filter ON by shake while the app is closed needs the accessibility
                // service. Re-checked on each lifecycle change so the hint clears immediately.
                val accessibilityEnabled = remember(lifecycleState) {
                    val enabled = android.provider.Settings.Secure.getString(
                        context.contentResolver,
                        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    ) ?: ""
                    enabled.contains(context.packageName, ignoreCase = true)
                }
                if (!accessibilityEnabled) {
                    InfoBanner(
                        text = "To turn the filter on by shaking while the app is closed, enable Night Shield in Accessibility settings.",
                        modifier = Modifier.padding(
                            start = Spacing.lg,
                            end = Spacing.lg,
                            bottom = Spacing.lg,
                        ),
                    )
                }
            }
        }
    }
}

// ── Sleep timer ───────────────────────────────────────────────────────────────

@Composable
private fun SleepTimerGroup() {
    val currentMinutes by NightShieldManager.sleepTimerMinutes.collectAsState()
    val options = listOf(0, 15, 30, 60, 90, 120, 180, 240)
    val labels = listOf(
        stringResource(R.string.sleep_timer_off),
        "15m", "30m", "1h", "1.5h", "2h", "3h", "4h",
    )

    SettingsGroup {
        SettingRow(
            icon = R.drawable.ic_timer_24,
            title = stringResource(R.string.sleep_timer_title),
            subtitle = stringResource(R.string.sleep_timer_subtitle),
            trailing = {
                // The running timer is not always one of the chip values: after a service restart
                // the remaining minutes are restored verbatim, so an index lookup alone would
                // report "Off" while a timer is actually counting down.
                val selectedIndex = options.indexOf(currentMinutes)
                Text(
                    text = when {
                        currentMinutes <= 0 -> stringResource(R.string.sleep_timer_off)
                        selectedIndex >= 0 -> labels[selectedIndex]
                        else -> "$currentMinutes min left"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        )
        Column(modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                options.indices.chunked(4).forEach { indices ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        indices.forEach { i ->
                            val selected = currentMinutes == options[i]
                            Surface(
                                onClick = { NightShieldManager.setSleepTimer(options[i]) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(Radius.sm),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = Spacing.md),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(labels[i], style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        repeat(4 - indices.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
