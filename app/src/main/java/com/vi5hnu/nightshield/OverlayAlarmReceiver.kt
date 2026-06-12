package com.vi5hnu.nightshield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

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
        val isRunning = NightShieldController.isActive(context)

        when (action) {
            ACTION_START -> if (!isRunning) {
                // targetIntensity is Pro-only; guard here as defense-in-depth against stale backup data
                if (intensity in 0.1f..1.0f && ProGate.isPro.value) NightShieldManager.setFilterIntensity(intensity)
                NightShieldController.activate(context)
            }

            ACTION_STOP -> if (isRunning) NightShieldController.deactivate(context)

            ACTION_SUNRISE -> NightShieldController.activate(context, sunrise = true)
        }
    }
}
