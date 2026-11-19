package com.vi5hnu.nightshield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles the Pause / Resume buttons on the "payment app not working?" notification.
 *
 * Only acts while [NightShieldService] is alive: the pause is carried out by the service, so a
 * button on a stale notification left behind by a killed process must do nothing rather than
 * leave a pause pending for the next session.
 */
class FilterPauseReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!NightShieldService.isRunning) return
        when (intent.action) {
            ACTION_PAUSE -> FilterPause.pause(context)
            ACTION_RESUME -> FilterPause.resume()
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.vi5hnu.nightshield.FILTER_PAUSE"
        const val ACTION_RESUME = "com.vi5hnu.nightshield.FILTER_RESUME"
    }
}
