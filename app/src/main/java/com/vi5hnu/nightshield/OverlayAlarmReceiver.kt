package com.vi5hnu.nightshield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class OverlayAlarmReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_ACTION    = "action"
        const val EXTRA_INTENSITY = "intensity"
        const val ACTION_START    = "start"
        const val ACTION_STOP     = "stop"
        /** PRO — Sunrise mode: start service and gradually fade out over 30 min. */
        const val ACTION_SUNRISE  = "sunrise"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action    = intent.getStringExtra(EXTRA_ACTION) ?: ACTION_START
        val intensity = intent.getFloatExtra(EXTRA_INTENSITY, -1f)
        val isRunning = OverlayHelpers.areOverlaysActive(context)

        when (action) {
            ACTION_START -> if (!isRunning) {
                // Apply scheduled intensity before starting (pro feature)
                if (intensity in 0.1f..1.0f) NightShieldManager.setFilterIntensity(intensity)
                startService(context, sunrise = false)
            }

            ACTION_STOP -> if (isRunning) stopService(context)

            ACTION_SUNRISE -> {
                // Sunrise: start if not already running, service handles gradual fade-out
                if (!isRunning) startService(context, sunrise = true)
                else {
                    // Already running — just signal sunrise mode to the running service
                    startService(context, sunrise = true)
                }
            }
        }
    }

    private fun startService(context: Context, sunrise: Boolean) {
        val serviceIntent = Intent(context, NightShieldService::class.java).apply {
            if (sunrise) putExtra(NightShieldService.EXTRA_SUNRISE, true)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
        OverlayHelpers.setOverlaysActive(context, true)
        NightShieldWidgetProvider.updateWidget(context)
    }

    private fun stopService(context: Context) {
        context.stopService(Intent(context, NightShieldService::class.java))
        OverlayHelpers.setOverlaysActive(context, false)
        NightShieldWidgetProvider.updateWidget(context)
    }
}
