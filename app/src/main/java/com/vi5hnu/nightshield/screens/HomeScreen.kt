package com.vi5hnu.nightshield.screens

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vi5hnu.nightshield.AppFilterConfig
import com.vi5hnu.nightshield.BackupHelper
import com.vi5hnu.nightshield.BillingManager
import com.vi5hnu.nightshield.FilterProfile
import com.vi5hnu.nightshield.NightShieldManager
import com.vi5hnu.nightshield.OverlayHelpers
import com.vi5hnu.nightshield.ProGate
import com.vi5hnu.nightshield.R
import com.vi5hnu.nightshield.ScheduleAction
import com.vi5hnu.nightshield.ScheduleEntry
import com.vi5hnu.nightshield.UsageTracker
import com.vi5hnu.nightshield.widgets.ColorDot
import com.vi5hnu.nightshield.widgets.ColorPicker
import com.vi5hnu.nightshield.widgets.ShakeDetector
import com.vi5hnu.nightshield.widgets.Tile
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toArgb

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    hasOverlayPermission: Boolean,
    areServicesActive: Boolean,
    launchOverlays: () -> Unit,
    stopOverlays: () -> Unit,
    allowShake: Boolean = true,
    onAllowShake: (Boolean) -> Unit,
    onPermissionRequest: () -> Unit,
    isPro: Boolean = false,
    triggerUpgradePrompt: Boolean = false,
    onUpgradePromptShown: () -> Unit = {},
    onPurchase: () -> Unit = {},
    onRestorePurchase: () -> Unit = {},
    onExportSettings: () -> Unit = {},
    onImportSettings: () -> Unit = {},
) {
    val context = LocalContext.current

    // Navigate to UpgradeScreen (full-screen overlay, no Jetpack Nav needed)
    var showUpgradeScreen by remember { mutableStateOf(false) }

    // Contextual upgrade prompt — fires once after sufficient engagement
    var showUpgradeDialog by remember { mutableStateOf(triggerUpgradePrompt) }
    if (showUpgradeDialog) {
        AlertDialog(
            onDismissRequest = { showUpgradeDialog = false; onUpgradePromptShown() },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text("Enjoying Night Shield?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Text(
                    "Unlock Smart Profiles, Blue Light Report, Gradual Fade-in and 8 more Pro features — one payment, no subscription, forever yours.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = { showUpgradeDialog = false; onUpgradePromptShown(); showUpgradeScreen = true },
                    shape = RoundedCornerShape(10.dp),
                ) { Text("See Pro Features") }
            },
            dismissButton = {
                TextButton(onClick = { showUpgradeDialog = false; onUpgradePromptShown() }) { Text("Not Now") }
            },
        )
    }

    if (showUpgradeScreen) {
        UpgradeScreen(
            isPro = isPro,
            onPurchase = onPurchase,
            onRestorePurchase = onRestorePurchase,
            onDismiss = { showUpgradeScreen = false },
        )
        return
    }

    // Shake detection (gated by allowShake)
    if (allowShake) {
        ShakeDetector {
            if (areServicesActive) stopOverlays()
            else if (hasOverlayPermission) launchOverlays()
            else onPermissionRequest()
        }
    }

    val haptic = LocalHapticFeedback.current
    val streakDays: Int = remember { OverlayHelpers.getStreakDays(context) }

    // Animations
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (areServicesActive) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    // Battery optimization banner state
    val showBatteryBanner by remember {
        derivedStateOf {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        }
    }

    // Color picker bottom sheet
    var showColorSheet by remember { mutableStateOf(false) }
    val canvasColor by NightShieldManager.canvasColor.collectAsState()
    // Capture the color before opening the sheet so we can revert if user dismisses without applying
    var colorBeforeSheet by remember { mutableStateOf(canvasColor) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "v2.0",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Scrollable body ───────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // Battery optimization banner
                if (showBatteryBanner) {
                    BatteryOptBanner(context = context)
                    Spacer(Modifier.height(12.dp))
                }

                // ── Status card ───────────────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = if (areServicesActive) Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                    )
                                ) else Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surface,
                                        MaterialTheme.colorScheme.surface,
                                    )
                                )
                            )
                    ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Moon icon with radial glow layers
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
                            if (areServicesActive) {
                                // Outer glow ring
                                Box(
                                    modifier = Modifier
                                        .size(140.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha * 0.12f),
                                            CircleShape
                                        )
                                )
                                // Inner glow ring
                                Box(
                                    modifier = Modifier
                                        .size(110.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha * 0.18f),
                                            CircleShape
                                        )
                                )
                            }
                            // Icon container
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(88.dp)
                                    .scale(iconScale)
                                    .background(
                                        brush = Brush.radialGradient(
                                            colors = if (areServicesActive) listOf(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                MaterialTheme.colorScheme.surfaceVariant
                                            ) else listOf(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                MaterialTheme.colorScheme.surface
                                            )
                                        ),
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = if (areServicesActive) 2.dp else 1.dp,
                                        brush = Brush.linearGradient(
                                            colors = if (areServicesActive)
                                                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                                            else
                                                listOf(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                        ),
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_moon_24),
                                    contentDescription = if (areServicesActive) stringResource(R.string.status_active) else stringResource(R.string.status_inactive),
                                    modifier = Modifier.size(44.dp),
                                    tint = if (areServicesActive) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        // Pulsing status dot + label
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (areServicesActive) Color(0xFF34D399).copy(alpha = glowAlpha)
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                        CircleShape
                                    )
                            )
                            Text(
                                text = if (areServicesActive) stringResource(R.string.status_active) else stringResource(R.string.status_inactive),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (areServicesActive) Color(0xFF34D399) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Streak badge — shown when user has ≥2 consecutive active days
                        if (streakDays >= 2) {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = "🔥 $streakDays day streak",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        // Action button
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                when {
                                    !hasOverlayPermission -> onPermissionRequest()
                                    areServicesActive -> stopOverlays()
                                    else -> launchOverlays()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when {
                                    !hasOverlayPermission -> MaterialTheme.colorScheme.secondary
                                    areServicesActive -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                contentColor = when {
                                    !hasOverlayPermission -> MaterialTheme.colorScheme.onSecondary
                                    areServicesActive -> MaterialTheme.colorScheme.error
                                    else -> Color.White
                                }
                            )
                        ) {
                            Text(
                                text = when {
                                    !hasOverlayPermission -> stringResource(R.string.btn_request_permission)
                                    areServicesActive -> stringResource(R.string.btn_stop)
                                    else -> stringResource(R.string.btn_activate)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (!hasOverlayPermission) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.permission_required_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    } // close gradient Box
                }

                Spacer(Modifier.height(24.dp))

                // ── Filter section ────────────────────────────────────────────
                SectionHeader("Filter")
                Spacer(Modifier.height(8.dp))

                SettingsCard {
                    // Shake toggle
                    Tile(R.drawable.vibration_24px, stringResource(R.string.shake_toggle_title), stringResource(R.string.shake_toggle_subtitle)) {
                        Switch(checked = allowShake, onCheckedChange = onAllowShake)
                    }

                    // Shake intensity — only visible when shake is enabled
                    AnimatedVisibility(
                        visible = allowShake,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        Column {
                            SettingsDivider()
                            val shakeIntensity by NightShieldManager.shakeIntensity.collectAsState()
                            Tile(
                                id = R.drawable.vibration_24px,
                                title = "Shake Sensitivity",
                                subtitle = "How hard you need to shake",
                            )
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                NightShieldManager.ShakeIntensity.entries.forEachIndexed { index, option ->
                                    SegmentedButton(
                                        selected = shakeIntensity == option,
                                        onClick = { NightShieldManager.setShakeIntensity(option) },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = index,
                                            count = NightShieldManager.ShakeIntensity.entries.size,
                                        ),
                                        label = {
                                            Text(
                                                option.label,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    SettingsDivider()

                    // Color (opens ModalBottomSheet — works when filter is inactive too)
                    Tile(R.drawable.format_paint_24px, stringResource(R.string.filter_color_title), stringResource(R.string.filter_color_subtitle)) {
                        ColorDot(color = canvasColor) {
                            colorBeforeSheet = canvasColor  // snapshot current color before opening
                            showColorSheet = true
                        }
                    }
                    SettingsDivider()

                    // Intensity slider
                    val intensity by NightShieldManager.filterIntensity.collectAsState()
                    Tile(R.drawable.ic_brightness_24, stringResource(R.string.filter_intensity_title), stringResource(R.string.filter_intensity_subtitle)) {
                        Slider(
                            value = intensity,
                            onValueChange = { NightShieldManager.setFilterIntensity(it) },
                            valueRange = 0.1f..1.0f,
                            modifier = Modifier.width(120.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    SettingsDivider()

                    // PRO — Gradual Fade-in
                    val gradualFade by NightShieldManager.gradualFadeEnabled.collectAsState()
                    Tile(
                        id =R.drawable.ic_brightness_24,
                        title = "Gradual Fade-in",
                        subtitle = "Filter eases in over 12 s instead of snapping on",
                    ) {
                        if (isPro) {
                            Switch(
                                checked = gradualFade,
                                onCheckedChange = { NightShieldManager.setGradualFadeEnabled(it) },
                            )
                        } else {
                            ProBadge { showUpgradeScreen = true }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Temperature presets ───────────────────────────────────────
                SectionHeader(stringResource(R.string.temperature_title))
                Spacer(Modifier.height(8.dp))

                TemperaturePresetRow()

                Spacer(Modifier.height(24.dp))

                // ── Sleep timer ───────────────────────────────────────────────
                SectionHeader(stringResource(R.string.sleep_timer_title))
                Spacer(Modifier.height(8.dp))

                SettingsCard {
                    SleepTimerRow()
                }

                Spacer(Modifier.height(24.dp))

                // ── Schedule ──────────────────────────────────────────────────
                SectionHeader(stringResource(R.string.schedule_title))
                Spacer(Modifier.height(8.dp))

                ScheduleSection(isPro = isPro, onShowUpgrade = { showUpgradeScreen = true })

                Spacer(Modifier.height(24.dp))

                // ── Per-app filter ────────────────────────────────────────────
                SectionHeader(stringResource(R.string.per_app_filter_title))
                Spacer(Modifier.height(8.dp))

                PerAppSection(isPro = isPro, onShowUpgrade = { showUpgradeScreen = true })

                Spacer(Modifier.height(24.dp))

                // ── PRO: Saved Profiles ───────────────────────────────────────
                SectionHeader("Profiles")
                Spacer(Modifier.height(8.dp))
                ProfilesSection(isPro = isPro, onShowUpgrade = { showUpgradeScreen = true })

                Spacer(Modifier.height(24.dp))

                // ── PRO: Blue Light Report ────────────────────────────────────
                SectionHeader("Blue Light Report")
                Spacer(Modifier.height(8.dp))
                BlueLightReportSection(isPro = isPro, onShowUpgrade = { showUpgradeScreen = true })

                Spacer(Modifier.height(24.dp))

                // ── PRO: App Theme ────────────────────────────────────────────
                SectionHeader("App Theme")
                Spacer(Modifier.height(8.dp))
                AppThemeSection(isPro = isPro, onShowUpgrade = { showUpgradeScreen = true })

                Spacer(Modifier.height(24.dp))

                // ── PRO: Widget Style ─────────────────────────────────────────
                SectionHeader("Widget")
                Spacer(Modifier.height(8.dp))
                WidgetStyleSection(isPro = isPro, onShowUpgrade = { showUpgradeScreen = true })

                Spacer(Modifier.height(24.dp))

                // ── PRO: Backup & Restore ─────────────────────────────────────
                SectionHeader("Backup & Restore")
                Spacer(Modifier.height(8.dp))
                BackupRestoreSection(
                    isPro = isPro,
                    onShowUpgrade = { showUpgradeScreen = true },
                    onExport = onExportSettings,
                    onImport = onImportSettings,
                )

                Spacer(Modifier.height(24.dp))

                // ── PRO: Tasker ───────────────────────────────────────────────
                SectionHeader("Automation")
                Spacer(Modifier.height(8.dp))
                TaskerSection(isPro = isPro, onShowUpgrade = { showUpgradeScreen = true })

                Spacer(Modifier.height(24.dp))
            }

            // ── Banner ad ─────────────────────────────────────────────────────
            BannerAd()

            // ── Footer ────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .defaultMinSize(minHeight = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.footer_text),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }
    }

    // ── Color picker ModalBottomSheet (works regardless of filter state) ─────
    // Behavior: dragging previews live on the overlay; Apply commits; dismiss/swipe-down reverts
    if (showColorSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                // User dismissed without pressing Apply → revert to the color before sheet opened
                NightShieldManager.setCanvasColor(colorBeforeSheet)
                showColorSheet = false
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = null
        ) {
            ColorPicker(
                initialColor = colorBeforeSheet,
                onChange = { NightShieldManager.setCanvasColor(it) },  // live preview while dragging
                onDismiss = { showColorSheet = false }  // Apply button: color already committed, just close
            )
        }
    }
}

// ── Banner ad ─────────────────────────────────────────────────────────────────

private const val BANNER_AD_UNIT_ID = "ca-app-pub-4715945578201106/5606751408"

@Composable
private fun BannerAd() {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BANNER_AD_UNIT_ID
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

// ── Reusable composables ──────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        thickness = 0.8.dp
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        content = { Column(Modifier.padding(vertical = 4.dp)) { content() } }
    )
}

// ── Temperature presets ───────────────────────────────────────────────────────

@Composable
private fun TemperaturePresetRow() {
    val activePreset by NightShieldManager.activePreset.collectAsState()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.temperature_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            // Two rows of 3 for better layout
            val presets = NightShieldManager.TemperaturePreset.entries
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { preset ->
                            val isSelected = activePreset == preset
                            PresetChip(
                                preset = preset,
                                isSelected = isSelected,
                                modifier = Modifier.weight(1f),
                                onClick = { NightShieldManager.applyTemperaturePreset(preset) }
                            )
                        }
                        // Fill remaining space if row has < 3 items
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
    onClick: () -> Unit
) {
    val bgColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    val borderColor = if (isSelected)
        MaterialTheme.colorScheme.primary
    else
        Color.Transparent

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(preset.dotColor, CircleShape)
            )
            Text(
                text = preset.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

// ── Sleep timer ───────────────────────────────────────────────────────────────

@Composable
private fun SleepTimerRow() {
    val currentMinutes by NightShieldManager.sleepTimerMinutes.collectAsState()
    val options = listOf(0, 15, 30, 60, 90, 120, 180, 240)
    val labels  = listOf("Off", "15m", "30m", "1h", "1.5h", "2h", "3h", "4h")

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
            text = stringResource(R.string.sleep_timer_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        // Split into rows of 4 so chips never get squished on narrow screens
        val rows = options.indices.chunked(4)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { indices ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    indices.forEach { i ->
                        val selected = currentMinutes == options[i]
                        FilterChip(
                            selected = selected,
                            onClick = { NightShieldManager.setSleepTimer(options[i]) },
                            label = { Text(labels[i], style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Pad last row if uneven
                    repeat(4 - indices.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// ── Schedule ──────────────────────────────────────────────────────────────────

@Composable
private fun ScheduleSection(isPro: Boolean, onShowUpgrade: () -> Unit) {
    val context = LocalContext.current
    val schedules by NightShieldManager.schedules.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    // Free users get 1 schedule; Pro is unlimited
    val canAddMore = isPro || schedules.isEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.schedule_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.schedule_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canAddMore) {
                    FilledTonalIconButton(onClick = { showAddDialog = true }) {
                        Icon(painterResource(R.drawable.ic_add_24), contentDescription = stringResource(R.string.schedule_add))
                    }
                } else {
                    ProBadge(onClick = onShowUpgrade)
                }
            }

            if (schedules.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                schedules.forEach { entry ->
                    ScheduleEntryRow(
                        entry = entry,
                        onToggle = { NightShieldManager.toggleScheduleEnabled(entry.id) },
                        onDelete = { NightShieldManager.removeSchedule(entry.id) }
                    )
                    if (entry != schedules.last()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            thickness = 0.8.dp
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap + to add a schedule",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showAddDialog) {
        AddScheduleDialog(
            context = context,
            onDismiss = { showAddDialog = false },
            onConfirm = { entry ->
                NightShieldManager.addSchedule(entry)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ScheduleEntryRow(
    entry: com.vi5hnu.nightshield.ScheduleEntry,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Action indicator dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    if (entry.action == ScheduleAction.ON) Color(0xFF34D399) else MaterialTheme.colorScheme.error,
                    CircleShape
                )
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.timeString,
                style = MaterialTheme.typography.titleMedium,
                color = if (entry.enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (entry.action == ScheduleAction.ON) stringResource(R.string.schedule_on) else stringResource(R.string.schedule_off),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = entry.enabled,
            onCheckedChange = { onToggle() },
            modifier = Modifier.padding(end = 4.dp)
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                painterResource(R.drawable.ic_delete_24),
                contentDescription = stringResource(R.string.schedule_remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AddScheduleDialog(
    context: Context,
    onDismiss: () -> Unit,
    onConfirm: (ScheduleEntry) -> Unit
) {
    var selectedHour by remember { mutableIntStateOf(22) }
    var selectedMinute by remember { mutableIntStateOf(0) }
    var selectedAction by remember { mutableStateOf(ScheduleAction.ON) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(stringResource(R.string.schedule_add), style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Time picker button
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, m ->
                                selectedHour = h
                                selectedMinute = m
                            },
                            selectedHour, selectedMinute, true
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_schedule_24),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "%02d:%02d".format(selectedHour, selectedMinute),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Action selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(ScheduleAction.ON, ScheduleAction.OFF).forEach { action ->
                        val isSelected = selectedAction == action
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedAction = action },
                            label = {
                                Text(
                                    if (action == ScheduleAction.ON) stringResource(R.string.schedule_on)
                                    else stringResource(R.string.schedule_off),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = {
                                Box(
                                    Modifier.size(8.dp).background(
                                        if (action == ScheduleAction.ON) Color(0xFF34D399) else MaterialTheme.colorScheme.error,
                                        CircleShape
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(ScheduleEntry(hour = selectedHour, minute = selectedMinute, action = selectedAction))
                },
                shape = RoundedCornerShape(10.dp)
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Battery optimization banner ────────────────────────────────────────────────

@Composable
private fun BatteryOptBanner(context: Context) {
    var dismissed by remember { mutableStateOf(false) }
    AnimatedVisibility(
        visible = !dismissed,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painterResource(R.drawable.ic_notification_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.battery_opt_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.battery_opt_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                }
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(stringResource(R.string.battery_opt_fix), style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(
                        onClick = { dismissed = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            stringResource(R.string.battery_opt_dismiss),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

// ── Per-app filter section ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PerAppSection(isPro: Boolean, onShowUpgrade: () -> Unit) {
    val configs by NightShieldManager.appFilterConfigs.collectAsState()
    val context = LocalContext.current
    var showAppPicker by remember { mutableStateOf(false) }

    // Accessibility service status — re-checked every time the lifecycle resumes
    // (so banner disappears immediately after user enables it in Settings and returns)
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val accessibilityEnabled = remember(lifecycleState) {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        enabledServices.contains(context.packageName, ignoreCase = true)
    }

    var showAccessibilityDialog by remember { mutableStateOf(false) }

    if (showAccessibilityDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDialog = false },
            title = {
                Text(
                    "Allow Accessibility Access",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    "Night Shield uses the Accessibility API only to detect which app is in the foreground, " +
                    "so it can automatically pause or adjust the blue light filter per app.\n\n" +
                    "It does NOT read any screen content, text, passwords, or personal data.\n\n" +
                    "Tapping \"Allow\" will open Android Accessibility Settings where you can enable " +
                    "\"Night Shield\" under Installed Services.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = {
                    showAccessibilityDialog = false
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) {
                    Text("Allow")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAccessibilityDialog = false }) {
                    Text("No Thanks")
                }
            }
        )
    }

    if (!accessibilityEnabled) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painterResource(R.drawable.ic_apps_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Accessibility service off",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Enable Night Shield in Accessibility Settings for per-app filter to work",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
                TextButton(
                    onClick = { showAccessibilityDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "Enable",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.per_app_filter_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.per_app_filter_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalIconButton(onClick = { showAppPicker = true }) {
                    Icon(painterResource(R.drawable.ic_add_24), contentDescription = "Add app")
                }
            }

            if (configs.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.per_app_no_items),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            } else {
                Spacer(Modifier.height(12.dp))
                configs.values.forEach { config ->
                    AppConfigRow(
                        config = config,
                        isPro = isPro,
                        onShowUpgrade = onShowUpgrade,
                        onUpdate = { NightShieldManager.setAppFilterConfig(it) },
                        onDelete = { NightShieldManager.removeAppFilterConfig(config.packageName) }
                    )
                    if (config != configs.values.last()) {
                        HorizontalDivider(
                            Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            thickness = 0.8.dp
                        )
                    }
                }
            }
        }
    }

    if (showAppPicker) {
        AppPickerSheet(
            context = context,
            alreadyAdded = configs.keys,
            onDismiss = { showAppPicker = false },
            onAppSelected = { pkg, label ->
                NightShieldManager.setAppFilterConfig(
                    com.vi5hnu.nightshield.AppFilterConfig(packageName = pkg, appLabel = label)
                )
                showAppPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppConfigRow(
    config: AppFilterConfig,
    isPro: Boolean,
    onShowUpgrade: () -> Unit,
    onUpdate: (AppFilterConfig) -> Unit,
    onDelete: () -> Unit,
) {
    var showColorSheet by remember { mutableStateOf(false) }
    val globalIntensity by NightShieldManager.filterIntensity.collectAsState()
    val globalColor by NightShieldManager.canvasColor.collectAsState()

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        // ── Primary row ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_apps_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    config.appLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    config.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.per_app_disable_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Switch(
                    checked = config.filterDisabled,
                    onCheckedChange = { onUpdate(config.copy(filterDisabled = it)) },
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    painterResource(R.drawable.ic_delete_24),
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // ── PRO: Intensity + Color controls (when filter is not disabled) ─────
        AnimatedVisibility(visible = !config.filterDisabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 34.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isPro) {
                    // Intensity
                    Text(
                        "Intensity",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = config.customIntensity ?: globalIntensity,
                        onValueChange = { onUpdate(config.copy(customIntensity = it)) },
                        valueRange = 0.1f..1.0f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.secondary,
                            activeTrackColor = MaterialTheme.colorScheme.secondary,
                        ),
                    )
                    // Color dot
                    ColorDot(
                        color = config.customColor ?: globalColor,
                    ) { showColorSheet = true }
                } else {
                    Text(
                        "Custom intensity & color",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    ProBadge(onClick = onShowUpgrade)
                }
            }
        }
    }

    // Per-app color picker sheet (Pro)
    if (showColorSheet && isPro) {
        ModalBottomSheet(
            onDismissRequest = { showColorSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = null,
        ) {
            ColorPicker(
                initialColor = config.customColor ?: globalColor,
                onChange = { onUpdate(config.copy(customColor = it)) },
                onDismiss = { showColorSheet = false },
            )
        }
    }
}

// ── Pro badge ─────────────────────────────────────────────────────────────────

@Composable
private fun ProBadge(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_moon_24),
                contentDescription = null,
                modifier = Modifier.size(12.dp),
            )
            Text("PRO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

// ── PRO: Profiles section ──────────────────────────────────────────────────────

@Composable
private fun ProfilesSection(isPro: Boolean, onShowUpgrade: () -> Unit) {
    val profiles by NightShieldManager.profiles.collectAsState()
    val canvasColor by NightShieldManager.canvasColor.collectAsState()
    val intensity by NightShieldManager.filterIntensity.collectAsState()
    var showSaveDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "Saved Profiles",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Save current filter as a named preset",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isPro) {
                    FilledTonalIconButton(onClick = { showSaveDialog = true }) {
                        Icon(painterResource(R.drawable.ic_add_24), contentDescription = "Save profile")
                    }
                } else {
                    ProBadge(onClick = onShowUpgrade)
                }
            }

            if (!isPro) {
                // Show locked preset cards so users see the shape of the value
                Spacer(Modifier.height(12.dp))
                data class LockedProfile(val name: String, val subtitle: String, val colorArgb: Int, val intensity: Float)
                listOf(
                    LockedProfile("Work Mode",   "Cool blue · low intensity",   0x663ABDE0.toInt(), 0.35f),
                    LockedProfile("Bedtime",     "Deep amber · high intensity", 0xCCFFA500.toInt(), 0.85f),
                    LockedProfile("Reading",     "Warm ivory · medium",         0xBBFFCC88.toInt(), 0.55f),
                    LockedProfile("Movie Night", "Crimson · cinematic",         0xCC8B0000.toInt(), 0.70f),
                    LockedProfile("Sunrise",     "Rose gold · gentle fade",     0xAAE91E63.toInt(), 0.45f),
                ).forEachIndexed { index, profile ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    Color(profile.colorArgb),
                                    RoundedCornerShape(10.dp),
                                )
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                profile.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            )
                            Text(
                                profile.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            )
                        }
                        // Intensity pill
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        ) {
                            Text(
                                "${(profile.intensity * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                        Icon(
                            painterResource(R.drawable.ic_moon_24),
                            contentDescription = "Locked",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    if (index < 4) HorizontalDivider(
                        Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        thickness = 0.6.dp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Upgrade to save your own profiles and instantly switch between them",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            if (isPro) {
                if (profiles.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No profiles yet. Save your current filter settings as a profile.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                    profiles.forEach { profile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        ComposeColor(profile.colorArgb).copy(alpha = profile.intensity),
                                        RoundedCornerShape(8.dp),
                                    )
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    profile.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    "Intensity ${(profile.intensity * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // Apply button
                            FilledTonalButton(
                                onClick = {
                                    NightShieldManager.setCanvasColor(ComposeColor(profile.colorArgb))
                                    NightShieldManager.setFilterIntensity(profile.intensity)
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text("Apply", style = MaterialTheme.typography.labelMedium)
                            }
                            IconButton(
                                onClick = { NightShieldManager.removeProfile(profile.id) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_delete_24),
                                    contentDescription = "Delete profile",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        if (profile != profiles.last()) {
                            HorizontalDivider(
                                Modifier.padding(vertical = 2.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                thickness = 0.8.dp,
                            )
                        }
                    }
                }
            }
        }
    }

    // Save profile dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false; newProfileName = "" },
            title = { Text("Save Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current: ${(intensity * 100).toInt()}% intensity", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text("Profile name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
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
                                )
                            )
                            newProfileName = ""
                            showSaveDialog = false
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false; newProfileName = "" }) { Text("Cancel") }
            },
        )
    }
}

// ── PRO: Blue Light Report section ────────────────────────────────────────────

@Composable
private fun BlueLightReportSection(isPro: Boolean, onShowUpgrade: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("This Week", style = MaterialTheme.typography.titleMedium)
                if (!isPro) ProBadge(onClick = onShowUpgrade)
            }

            Spacer(Modifier.height(12.dp))

            val usage = remember { UsageTracker.getWeeklyUsage(context) }
            val maxMinutes = usage.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1

            if (isPro) {
                val totalHours = usage.sumOf { it.second } / 60f
                Text(
                    "%.1f hrs total this week".format(totalHours),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
            }

            // Bar chart — full for Pro, blurred teaser for free
            Box {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    usage.forEachIndexed { index, (date, minutes) ->
                        val dayLabel = date.takeLast(5)
                        // Free users only see real data for today (last entry)
                        val displayMinutes = if (isPro || index == usage.lastIndex) minutes else 30
                        val barFraction = displayMinutes.toFloat() / maxMinutes.coerceAtLeast(30)
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((barFraction * 60).dp.coerceAtLeast(2.dp))
                                    .background(
                                        if (isPro || index == usage.lastIndex)
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f + 0.3f * barFraction)
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                                    )
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                dayLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isPro || index == usage.lastIndex)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                maxLines = 1,
                            )
                        }
                    }
                }

                // Free overlay: lock prompt on top of the greyed bars
                if (!isPro) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    0f to MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                    0.15f to MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Unlock 7-day history",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            ProBadge(onClick = onShowUpgrade)
                        }
                    }
                }
            }
        }
    }
}

// ── PRO: App Theme section ─────────────────────────────────────────────────────

@Composable
private fun AppThemeSection(isPro: Boolean, onShowUpgrade: () -> Unit) {
    val currentTheme by NightShieldManager.appTheme.collectAsState()
    SettingsCard {
        NightShieldManager.AppTheme.entries.forEachIndexed { index, theme ->
            Tile(
                id =R.drawable.ic_moon_24,
                title = theme.label,
                subtitle = when (theme) {
                    NightShieldManager.AppTheme.SYSTEM       -> "Default deep indigo dark look"
                    NightShieldManager.AppTheme.DARK_OLED    -> "Pure black — saves battery on OLED"
                    NightShieldManager.AppTheme.WARM         -> "Amber-tinted dark to match the filter"
                    NightShieldManager.AppTheme.BLUE_NIGHT   -> "Deep navy — calm coding & reading"
                    NightShieldManager.AppTheme.FOREST       -> "Emerald green — easy on the eyes"
                    NightShieldManager.AppTheme.PURPLE_NIGHT -> "Galaxy purple — AMOLED aesthetic"
                },
            ) {
                if (isPro || theme == NightShieldManager.AppTheme.SYSTEM) {
                    RadioButton(
                        selected = currentTheme == theme,
                        onClick = { NightShieldManager.setAppTheme(theme) },
                    )
                } else {
                    ProBadge(onClick = onShowUpgrade)
                }
            }
            if (index < NightShieldManager.AppTheme.entries.lastIndex) SettingsDivider()
        }
    }
}

// ── PRO: Widget Style section ──────────────────────────────────────────────────

@Composable
private fun WidgetStyleSection(isPro: Boolean, onShowUpgrade: () -> Unit) {
    val currentStyle by NightShieldManager.widgetStyle.collectAsState()
    SettingsCard {
        NightShieldManager.WidgetStyle.entries.forEachIndexed { index, style ->
            Tile(
                id =R.drawable.ic_apps_24,
                title = style.label,
                subtitle = when (style) {
                    NightShieldManager.WidgetStyle.STANDARD -> "Icon + toggle button"
                    NightShieldManager.WidgetStyle.MINIMAL  -> "Icon only — smallest footprint"
                    NightShieldManager.WidgetStyle.DETAILED -> "Icon + status + intensity"
                },
            ) {
                if (isPro) {
                    RadioButton(
                        selected = currentStyle == style,
                        onClick = { NightShieldManager.setWidgetStyle(style) },
                    )
                } else {
                    ProBadge(onClick = onShowUpgrade)
                }
            }
            if (index < NightShieldManager.WidgetStyle.entries.lastIndex) SettingsDivider()
        }
    }
}

// ── PRO: Backup & Restore section ─────────────────────────────────────────────

@Composable
private fun BackupRestoreSection(
    isPro: Boolean,
    onShowUpgrade: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    SettingsCard {
        Tile(
            id =R.drawable.ic_schedule_24,
            title = "Export Settings",
            subtitle = "Save all settings to a JSON file",
        ) {
            if (isPro) {
                FilledTonalButton(
                    onClick = onExport,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) { Text("Export", style = MaterialTheme.typography.labelMedium) }
            } else {
                ProBadge(onClick = onShowUpgrade)
            }
        }
        SettingsDivider()
        Tile(
            id =R.drawable.ic_schedule_24,
            title = "Import Settings",
            subtitle = "Restore settings from a backup file",
        ) {
            if (isPro) {
                FilledTonalButton(
                    onClick = onImport,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) { Text("Import", style = MaterialTheme.typography.labelMedium) }
            } else {
                ProBadge(onClick = onShowUpgrade)
            }
        }
    }
}

// ── PRO: Tasker / Automation section ──────────────────────────────────────────

@Composable
private fun TaskerSection(isPro: Boolean, onShowUpgrade: () -> Unit) {
    SettingsCard {
        Tile(
            id =R.drawable.ic_schedule_24,
            title = "Tasker Integration",
            subtitle = "Automate Night Shield via intents",
        ) {
            if (!isPro) ProBadge(onClick = onShowUpgrade)
        }
        if (isPro) {
            SettingsDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Send these broadcast intents from Tasker, Shortcuts, or adb:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                listOf(
                    "ACTION_FILTER_ON" to "Turn filter on",
                    "ACTION_FILTER_OFF" to "Turn filter off",
                    "ACTION_FILTER_TOGGLE" to "Toggle filter",
                ).forEach { (action, desc) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "com.vi5hnu.nightshield.$action",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Optional extra: intensity (Float 0.1–1.0)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Installed app picker bottom sheet ─────────────────────────────────────────

// Icon not stored in AppInfo — loaded lazily per visible item on IO thread
private data class AppInfo(val packageName: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSheet(
    context: Context,
    alreadyAdded: Set<String>,
    onDismiss: () -> Unit,
    onAppSelected: (packageName: String, label: String) -> Unit
) {
    var query by remember { mutableStateOf("") }

    // Load app list on IO thread so the button tap never blocks the main thread
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            // queryIntentActivities with ACTION_MAIN + CATEGORY_LAUNCHER returns
            // every app that has a launcher icon, including system apps (Chrome,
            // Camera, etc.) that getInstalledApplications(0) silently drops on
            // Android 11+.  The <queries> intent block in the manifest makes this
            // work without QUERY_ALL_PACKAGES.
            val launchIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            pm.queryIntentActivities(launchIntent, PackageManager.MATCH_ALL)
                .map { it.activityInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != context.packageName }
                .map { info ->
                    AppInfo(
                        packageName = info.packageName,
                        label = info.loadLabel(pm).toString()
                    )
                }
                .sortedBy { it.label.lowercase() }
        }
        allApps = apps
        isLoading = false
    }

    val filtered = remember(query, allApps, alreadyAdded) {
        allApps.filter { app ->
            app.packageName !in alreadyAdded &&
            (query.isBlank() || app.label.contains(query, ignoreCase = true) ||
             app.packageName.contains(query, ignoreCase = true))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.85f)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Choose App", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss) {
                    Icon(painterResource(R.drawable.ic_close_24), contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp))
                }
            }

            // Search bar
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps…", style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )

            Spacer(Modifier.height(8.dp))

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
                filtered.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (query.isBlank()) "No apps found" else "No apps match \"$query\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(filtered, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAppSelected(app.packageName, app.label) }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Load icon lazily on IO thread — null until ready, shows placeholder
                                val iconBitmap by produceState<ImageBitmap?>(null, app.packageName) {
                                    value = withContext(Dispatchers.IO) {
                                        runCatching {
                                            context.packageManager
                                                .getApplicationIcon(app.packageName)
                                                .toBitmap(72, 72)
                                                .asImageBitmap()
                                        }.getOrNull()
                                    }
                                }

                                if (iconBitmap != null) {
                                    Image(
                                        bitmap = iconBitmap!!,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painterResource(R.drawable.ic_apps_24),
                                            contentDescription = null,
                                            modifier = Modifier.size(22.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        app.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        app.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                thickness = 0.5.dp
                            )
                        }
                        item { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
                    }
                }
            }
        }
    }
}
