package com.vi5hnu.nightshield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Tasker / Shortcuts / Intent-based automation receiver.
 *
 * Supported actions (send via adb or Tasker "Send Intent"):
 *   com.vi5hnu.nightshield.ACTION_FILTER_ON      – Start the filter
 *   com.vi5hnu.nightshield.ACTION_FILTER_OFF     – Stop the filter
 *   com.vi5hnu.nightshield.ACTION_FILTER_TOGGLE  – Toggle filter on/off
 *
 * Optional extra (Float, 0.1 – 1.0):
 *   "intensity"  – Set filter intensity before starting (ACTION_ON / TOGGLE)
 *
 * Example adb command:
 *   adb shell am broadcast -a com.vi5hnu.nightshield.ACTION_FILTER_ON \
 *       --ef intensity 0.7 -n com.vi5hnu.nightshield/.TaskerReceiver
 *
 * NOTE: This receiver requires Pro. Non-pro installs will ignore the intent.
 */
class TaskerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Gate behind Pro — Tasker integration is a premium feature
        if (!ProGate.isPro.value) return

        val intensity = intent.getFloatExtra(EXTRA_INTENSITY, -1f)
        if (intensity in 0.1f..1.0f) {
            NightShieldManager.setFilterIntensity(intensity)
        }

        val isRunning = NightShieldController.isActive(context)

        when (intent.action) {
            ACTION_ON -> if (!isRunning) NightShieldController.activate(context)
            ACTION_OFF -> if (isRunning) NightShieldController.deactivate(context)
            ACTION_TOGGLE -> NightShieldController.toggle(context)
        }
    }

    companion object {
        const val ACTION_ON     = "com.vi5hnu.nightshield.ACTION_FILTER_ON"
        const val ACTION_OFF    = "com.vi5hnu.nightshield.ACTION_FILTER_OFF"
        const val ACTION_TOGGLE = "com.vi5hnu.nightshield.ACTION_FILTER_TOGGLE"
        const val EXTRA_INTENSITY = "intensity"
    }
}
