package com.vi5hnu.nightshield

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit

/**
 * PRO — "Follow system dark mode": switches the filter on when the system enters dark mode.
 *
 * Android sends no broadcast for a night-mode change, so it is checked wherever a running
 * component receives the new configuration: [MainActivity] on resume and [ShakeMonitorService]
 * (alive in the background whenever the filter is off and background shake is on).
 * Reads prefs rather than [NightShieldManager] so it is correct in a cold service process.
 */
object DarkModeSync {

    private const val PREFS = "dark_mode_sync"
    private const val KEY_LAST_NIGHT = "last_seen_night"

    /**
     * Turns the filter on when the system has *switched into* dark mode since the last check.
     * Returns true if it was activated.
     *
     * Edge-triggered on purpose: reacting to "is dark" (level) re-enabled the filter on every
     * resume and every configuration change (rotation), so a user who switched it off while in
     * dark mode could not keep it off. The last seen state is recorded even when the feature is
     * off, so enabling it later does not fire on a stale transition.
     */
    fun maybeActivate(context: Context, configuration: Configuration): Boolean {
        val isNight = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wasNight = prefs.getBoolean(KEY_LAST_NIGHT, isNight)
        if (wasNight != isNight) prefs.edit { putBoolean(KEY_LAST_NIGHT, isNight) }

        if (!isNight || wasNight) return false
        if (!ProGate.isPro.value || !OverlayHelpers.loadDarkModeSync(context)) return false
        if (NightShieldController.isActive(context)) return false
        return NightShieldController.activate(context)
    }
}
