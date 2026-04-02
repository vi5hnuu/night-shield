package com.vi5hnu.nightshield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Restarts the overlay service after device reboot if the filter was active
 * and the overlay permission is still granted.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) return

        if (OverlayHelpers.areOverlaysActive(context) && OverlayHelpers.checkOverlayPermission(context)) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, NightShieldService::class.java)
            )
        }
        NightShieldWidgetProvider.updateWidget(context)

        // Re-schedule any active alarms that were lost on reboot
        val schedules = OverlayHelpers.loadSchedules(context)
        if (schedules.isNotEmpty()) {
            AlarmHelpers.scheduleAll(context, schedules)
        }
    }
}
