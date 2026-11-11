package com.vi5hnu.nightshield.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.vi5hnu.nightshield.FilterPause
import com.vi5hnu.nightshield.ui.components.SegmentedChips
import com.vi5hnu.nightshield.ui.components.SettingsGroup
import com.vi5hnu.nightshield.ui.theme.Spacing

/**
 * Explains the Pause notification and lets the user choose how long one pause lasts.
 *
 * The explanation matters as much as the setting: people who hit "payment blocked" need to learn
 * that the fix is one tap in the notification shade, not uninstalling the app. Free for everyone.
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
        }
    }
}
