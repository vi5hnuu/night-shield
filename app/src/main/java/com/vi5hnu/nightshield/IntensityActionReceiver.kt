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
        // Read the saved value (not the in-memory flow) so this is correct even in a cold process
        // spun up just to handle a widget tap.
        val (color, current, allowShake) = OverlayHelpers.loadFilterSettings(context)
        val next = when (intent.action) {
            ACTION_INCREASE -> (current + 0.10f).coerceAtMost(1.0f)
            ACTION_DECREASE -> (current - 0.10f).coerceAtLeast(0.1f)
            else -> return
        }
        NightShieldManager.setFilterIntensity(next)       // live overlay (if the service is running)
        OverlayHelpers.saveFilterSettings(context, color, next, allowShake)
        IntensityWidgetProvider.updateAll(context)        // refresh the % shown on the widget
    }

    companion object {
        const val ACTION_INCREASE = "com.vi5hnu.nightshield.INTENSITY_UP"
        const val ACTION_DECREASE = "com.vi5hnu.nightshield.INTENSITY_DOWN"
    }
}
