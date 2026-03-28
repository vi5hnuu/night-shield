package com.vi5hnu.nightshield.screens

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vi5hnu.nightshield.NightShieldManager
import com.vi5hnu.nightshield.R
import com.vi5hnu.nightshield.ScheduleAction
import com.vi5hnu.nightshield.ScheduleEntry
import com.vi5hnu.nightshield.widgets.ColorDot
import com.vi5hnu.nightshield.widgets.ColorPicker
import com.vi5hnu.nightshield.widgets.ShakeDetector
import com.vi5hnu.nightshield.widgets.Tile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    hasOverlayPermission: Boolean,
    areServicesActive: Boolean,
    launchOverlays: () -> Unit,
    stopOverlays: () -> Unit,
    allowShake: Boolean = true,
    onAllowShake: (Boolean) -> Unit,
    onPermissionRequest: () -> Unit
) {
    val context = LocalContext.current

    // Shake detection (gated by allowShake — correctly wired now)
    if (allowShake) {
        ShakeDetector {
            if (areServicesActive) stopOverlays()
            else if (hasOverlayPermission) launchOverlays()
            else onPermissionRequest()
        }
    }

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

                        Spacer(Modifier.height(24.dp))

                        // Action button
                        Button(
                            onClick = {
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

                ScheduleSection()

                Spacer(Modifier.height(24.dp))

                // ── Per-app filter ────────────────────────────────────────────
                SectionHeader(stringResource(R.string.per_app_filter_title))
                Spacer(Modifier.height(8.dp))

                PerAppSection()

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
private fun ScheduleSection() {
    val context = LocalContext.current
    val schedules by NightShieldManager.schedules.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

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
                FilledTonalIconButton(onClick = { showAddDialog = true }) {
                    Icon(painterResource(R.drawable.ic_add_24), contentDescription = stringResource(R.string.schedule_add))
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
private fun PerAppSection() {
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
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
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

@Composable
private fun AppConfigRow(
    config: com.vi5hnu.nightshield.AppFilterConfig,
    onUpdate: (com.vi5hnu.nightshield.AppFilterConfig) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            painterResource(R.drawable.ic_apps_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                config.appLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                config.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                stringResource(R.string.per_app_disable_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Switch(
                checked = config.filterDisabled,
                onCheckedChange = { onUpdate(config.copy(filterDisabled = it)) },
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                painterResource(R.drawable.ic_delete_24),
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
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
            pm.getInstalledApplications(0)
                .filter { info ->
                    info.flags and ApplicationInfo.FLAG_SYSTEM == 0 &&
                    info.packageName != context.packageName
                }
                .map { info ->
                    AppInfo(
                        packageName = info.packageName,
                        label = pm.getApplicationLabel(info).toString()
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
                    Icon(painterResource(R.drawable.ic_delete_24), contentDescription = "Close", modifier = Modifier.size(20.dp))
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
