package com.vi5hnu.nightshield.screens.schedule

import android.app.TimePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vi5hnu.nightshield.NightShieldManager
import com.vi5hnu.nightshield.OverlayHelpers
import com.vi5hnu.nightshield.R
import com.vi5hnu.nightshield.ScheduleAction
import com.vi5hnu.nightshield.ScheduleEntry
import com.vi5hnu.nightshield.SunTimes
import com.vi5hnu.nightshield.screens.NightShieldActions
import com.vi5hnu.nightshield.ui.components.EmptyState
import com.vi5hnu.nightshield.ui.components.ProBadge
import com.vi5hnu.nightshield.ui.components.SectionHeader
import com.vi5hnu.nightshield.ui.components.SettingRow
import com.vi5hnu.nightshield.ui.components.SettingsDivider
import com.vi5hnu.nightshield.ui.components.SettingsGroup
import com.vi5hnu.nightshield.ui.theme.IconSize
import com.vi5hnu.nightshield.ui.theme.Radius
import com.vi5hnu.nightshield.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Colour that identifies each schedule action consistently across rows and chips. */
@Composable
private fun actionColor(action: ScheduleAction): Color = when (action) {
    ScheduleAction.ON -> MaterialTheme.colorScheme.primary
    ScheduleAction.OFF -> MaterialTheme.colorScheme.error
    ScheduleAction.SUNRISE -> MaterialTheme.colorScheme.secondary
}

/**
 * Everything time-based: fixed schedules, the sunset/sunrise follower, and the reminders that fire
 * while the filter runs.
 */
@Composable
fun ScheduleScreen(
    isPro: Boolean,
    actions: NightShieldActions,
    onShowUpgrade: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
    ) {
        Spacer(Modifier.height(Spacing.sm))

        SectionHeader(stringResource(R.string.schedule_title))
        SchedulesGroup(isPro = isPro, onShowUpgrade = onShowUpgrade)

        Spacer(Modifier.height(Spacing.xxl))

        SectionHeader("Sunset & sunrise")
        AutoScheduleGroup(
            isPro = isPro,
            onShowUpgrade = onShowUpgrade,
            onEnable = actions.onEnableAutoSchedule,
            onDisable = actions.onDisableAutoSchedule,
            onRefresh = actions.onRefreshLocation,
        )

        Spacer(Modifier.height(Spacing.xxl))

        SectionHeader("Reminders & automation")
        SettingsGroup {
            val eyeBreak by NightShieldManager.eyeBreakEnabled.collectAsState()
            SettingRow(
                icon = R.drawable.ic_visibility_24,
                title = "20-20-20 eye breaks",
                subtitle = "Every 20 minutes, a reminder to look 20 feet away for 20 seconds",
                trailing = {
                    Switch(
                        checked = eyeBreak,
                        onCheckedChange = {
                            NightShieldManager.setEyeBreakEnabled(it)
                            OverlayHelpers.saveEyeBreakEnabled(context, it)
                        },
                    )
                },
            )
            SettingsDivider()

            val darkSync by NightShieldManager.darkModeAutoSync.collectAsState()
            SettingRow(
                icon = R.drawable.ic_dark_mode_24,
                title = "Follow system dark mode",
                subtitle = "Turn the filter on when the system switches to dark mode",
                trailing = {
                    Switch(
                        checked = darkSync,
                        onCheckedChange = {
                            NightShieldManager.setDarkModeAutoSync(it)
                            OverlayHelpers.saveDarkModeSync(context, it)
                        },
                    )
                },
            )
        }

        Spacer(Modifier.height(Spacing.xxxl))
    }
}

// ── Fixed schedules ───────────────────────────────────────────────────────────

@Composable
private fun SchedulesGroup(isPro: Boolean, onShowUpgrade: () -> Unit) {
    val context = LocalContext.current
    val schedules by NightShieldManager.schedules.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    // Free users get one schedule; Pro is unlimited.
    val canAddMore = isPro || schedules.isEmpty()

    SettingsGroup {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg, end = Spacing.md, top = Spacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.schedule_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canAddMore) {
                FilledTonalIconButton(
                    onClick = { showAddDialog = true },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_add_24),
                        contentDescription = stringResource(R.string.schedule_add),
                    )
                }
            } else {
                ProBadge(onClick = onShowUpgrade)
            }
        }

        if (schedules.isEmpty()) {
            EmptyState(
                icon = R.drawable.ic_alarm_24,
                title = "No schedules yet",
                description = "Add a time to turn the filter on or off automatically.",
            )
        } else {
            Spacer(Modifier.height(Spacing.sm))
            schedules.forEachIndexed { index, entry ->
                ScheduleRow(
                    entry = entry,
                    onToggle = { NightShieldManager.toggleScheduleEnabled(entry.id) },
                    onDelete = { NightShieldManager.removeSchedule(entry.id) },
                )
                if (index < schedules.lastIndex) SettingsDivider()
            }
            Spacer(Modifier.height(Spacing.sm))
        }
    }

    if (showAddDialog) {
        AddScheduleDialog(
            context = context,
            isPro = isPro,
            onDismiss = { showAddDialog = false },
            onConfirm = { entry ->
                val isDuplicate = schedules.any {
                    it.hour == entry.hour && it.minute == entry.minute && it.action == entry.action
                }
                if (isDuplicate) {
                    Toast.makeText(
                        context,
                        "A schedule at that time already exists",
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    NightShieldManager.addSchedule(entry)
                }
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun ScheduleRow(
    entry: ScheduleEntry,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(actionColor(entry.action), CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.timeString,
                style = MaterialTheme.typography.titleMedium,
                color = if (entry.enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Text(
                text = when (entry.action) {
                    ScheduleAction.ON -> stringResource(R.string.schedule_on)
                    ScheduleAction.OFF -> stringResource(R.string.schedule_off)
                    ScheduleAction.SUNRISE -> "Sunrise fade-out"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = entry.enabled, onCheckedChange = { onToggle() })
        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(
                painterResource(R.drawable.ic_delete_24),
                contentDescription = stringResource(R.string.schedule_remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.sm + 2.dp),
            )
        }
    }
}

@Composable
private fun AddScheduleDialog(
    context: Context,
    isPro: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ScheduleEntry) -> Unit,
) {
    var selectedHour by remember { mutableIntStateOf(22) }
    var selectedMinute by remember { mutableIntStateOf(0) }
    var selectedAction by remember { mutableStateOf(ScheduleAction.ON) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.schedule_add), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, m ->
                                selectedHour = h
                                selectedMinute = m
                            },
                            selectedHour, selectedMinute, true,
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.sm),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_schedule_24),
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.sm + 2.dp),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        "%02d:%02d".format(selectedHour, selectedMinute),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        listOf(ScheduleAction.ON, ScheduleAction.OFF).forEach { action ->
                            FilterChip(
                                selected = selectedAction == action,
                                onClick = { selectedAction = action },
                                label = {
                                    Text(
                                        when (action) {
                                            ScheduleAction.ON -> stringResource(R.string.schedule_on)
                                            else -> stringResource(R.string.schedule_off)
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                                leadingIcon = {
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .background(actionColor(action), CircleShape),
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    // Sunrise fade-out is Pro-only, so it gets its own full-width row.
                    if (isPro) {
                        FilterChip(
                            selected = selectedAction == ScheduleAction.SUNRISE,
                            onClick = { selectedAction = ScheduleAction.SUNRISE },
                            label = {
                                Text(
                                    "Sunrise fade-out — gradually removes the filter",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            leadingIcon = {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .background(actionColor(ScheduleAction.SUNRISE), CircleShape),
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        ScheduleEntry(
                            hour = selectedHour,
                            minute = selectedMinute,
                            action = selectedAction,
                        ),
                    )
                },
                shape = RoundedCornerShape(Radius.sm),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ── Auto sunset / sunrise ─────────────────────────────────────────────────────

@Composable
private fun AutoScheduleGroup(
    isPro: Boolean,
    onShowUpgrade: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val enabled by NightShieldManager.autoScheduleEnabled.collectAsState()
    val city by NightShieldManager.autoCity.collectAsState()

    SettingsGroup {
        SettingRow(
            icon = R.drawable.ic_twilight_24,
            title = "Auto sunset / sunrise",
            subtitle = "The filter follows your local sunset and sunrise times",
            enabled = isPro,
            onClick = if (isPro) null else onShowUpgrade,
            trailing = {
                if (isPro) {
                    Switch(
                        checked = enabled,
                        onCheckedChange = { if (it) onEnable() else onDisable() },
                    )
                } else {
                    ProBadge(onClick = onShowUpgrade)
                }
            },
        )

        if (isPro && enabled) {
            SettingsDivider()
            val location = remember(city) { OverlayHelpers.loadAutoLocation(context) }
            val times = remember(location) {
                location?.let { SunTimes.compute(it.lat, it.lon, LocalDate.now()) }
            }
            val zone = ZoneId.systemDefault()
            val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
            fun format(ms: Long) =
                Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().format(formatter)

            val sunrise = times?.let { format(it.first) } ?: "--:--"
            val sunset = times?.let { format(it.second) } ?: "--:--"

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.lg, end = Spacing.sm, bottom = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_location_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(IconSize.sm),
                        )
                        Text(
                            city.ifBlank { "Location set" },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TimeHint(icon = R.drawable.ic_bedtime_24, label = "On $sunset")
                        TimeHint(icon = R.drawable.ic_light_mode_24, label = "Off $sunrise")
                    }
                }
                TextButton(onClick = onRefresh) {
                    Icon(
                        painterResource(R.drawable.ic_refresh_24),
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.sm + 2.dp),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Refresh", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun TimeHint(icon: Int, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.sm),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
