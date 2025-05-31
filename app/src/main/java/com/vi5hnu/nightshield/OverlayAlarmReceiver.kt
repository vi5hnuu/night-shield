package com.vi5hnu.nightshield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class OverlayAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!OverlayHelpers.areOverlaysActive(context)) {
            Log.d("ALARM RECEIVED","Received alary")
            val serviceIntent = Intent(context, NightShieldService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
            OverlayHelpers.setOverlaysActive(context, true)
        }
    }
}
