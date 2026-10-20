package com.vi5hnu.nightshield

import android.content.Context
import android.content.res.Configuration

/**
 * PRO — "Follow system dark mode": switches the filter on while the system is in dark mode.
 *
 * Android sends no broadcast for a night-mode change, so it is checked wherever a running
 * component receives the new configuration: [MainActivity] on resume and [ShakeMonitorService]
 * (alive in the background whenever the filter is off and background shake is on).
 * Reads prefs rather than [NightShieldManager] so it is correct in a cold service process.
 */
object DarkModeSync {

    /** Turns the filter on if the feature applies right now. Returns true if it was activated. */
    fun maybeActivate(context: Context, configuration: Configuration): Boolean {
        if (!ProGate.isPro.value || !OverlayHelpers.loadDarkModeSync(context)) return false
        val isNight = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        if (!isNight || NightShieldController.isActive(context)) return false
        return NightShieldController.activate(context)
    }
}
