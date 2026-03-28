package com.vi5hnu.nightshield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class OverlayAlarmReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_ACTION = "action"
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION) ?: ACTION_START
        val isRunning = OverlayHelpers.areOverlaysActive(context)

        if (action == ACTION_START && !isRunning) {
            val serviceIntent = Intent(context, NightShieldService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
            OverlayHelpers.setOverlaysActive(context, true)
        } else if (action == ACTION_STOP && isRunning) {
            context.stopService(Intent(context, NightShieldService::class.java))
            OverlayHelpers.setOverlaysActive(context, false)
        }
    }
}
