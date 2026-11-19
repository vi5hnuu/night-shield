package com.vi5hnu.nightshield.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.vi5hnu.nightshield.FilterPause
import com.vi5hnu.nightshield.FilterPauseTileService
import com.vi5hnu.nightshield.R
import com.vi5hnu.nightshield.ui.components.SegmentedChips
import com.vi5hnu.nightshield.ui.components.SettingsGroup
import com.vi5hnu.nightshield.ui.theme.IconSize
import com.vi5hnu.nightshield.ui.theme.Spacing

/**
 * Explains the Pause notification, lets the user choose how long one pause lasts, and offers the
 * Pause Quick Settings tile.
 *
 * The explanation matters as much as the setting: people who hit "payment blocked" need to learn
 * that the fix is one tap in the notification shade, not uninstalling the app. Free for everyone.
 * Android 13+ can add the tile from here; older versions are told where to find it.
 */
@Composable
internal fun PauseSettings() {
    val context = LocalContext.current
    var duration by remember { mutableStateOf(FilterPause.duration(context)) }

    SettingsGroup {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
                "Pause length",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Apps like Paytm, PhonePe and banking apps can block payments while a screen " +
                    "filter is on. Pull down your notifications and tap \"Payment app not working?\": " +
                    "the filter steps aside for this long, then returns by itself.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
            SegmentedChips(
                options = FilterPause.Duration.entries,
                selected = duration,
                label = { it.label },
                onSelect = {
                    duration = it
                    FilterPause.setDuration(context, it)
                },
            )
            Spacer(Modifier.height(Spacing.md))
            if (FilterPauseTileService.canRequestAdd) {
                OutlinedButton(
                    onClick = { FilterPauseTileService.requestAdd(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pause_24),
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.sm),
                    )
                    Spacer(Modifier.size(Spacing.sm))
                    Text("Add Pause tile to Quick Settings")
                }
            } else {
                Text(
                    "Tip: add the \"Pause filter\" tile from your Quick Settings edit screen for a " +
                        "one-tap pause.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
