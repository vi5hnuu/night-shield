package com.vi5hnu.nightshield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives notification quick-action broadcasts to bump intensity up/down by 10 pp.
 * Battery-safe: only fires when the user explicitly taps a notification button.
 */
class IntensityActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val current = NightShieldManager.filterIntensity.value
        val next = when (intent.action) {
            ACTION_INCREASE -> (current + 0.10f).coerceAtMost(1.0f)
            ACTION_DECREASE -> (current - 0.10f).coerceAtLeast(0.1f)
            else -> return
        }
        NightShieldManager.setFilterIntensity(next)
        // Persist so the new value survives a service restart
        OverlayHelpers.saveFilterSettings(
            context,
            NightShieldManager.canvasColor.value,
            next,
            NightShieldManager.allowShake.value,
        )
    }

    companion object {
        const val ACTION_INCREASE = "com.vi5hnu.nightshield.INTENSITY_UP"
        const val ACTION_DECREASE = "com.vi5hnu.nightshield.INTENSITY_DOWN"
    }
}
