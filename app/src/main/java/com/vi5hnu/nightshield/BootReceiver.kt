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

        // Init billing from cache so ProGate.isPro reflects entitlement before we schedule alarms
        BillingManager.init(context)

        if (OverlayHelpers.areOverlaysActive(context) && OverlayHelpers.checkOverlayPermission(context)) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, NightShieldService::class.java)
            )
        }
        NightShieldWidgetProvider.updateWidget(context)

        // Re-schedule alarms lost on reboot; respect free-tier cap in case Pro was revoked offline
        val schedules = OverlayHelpers.loadSchedules(context)
        val schedulesToActivate = if (ProGate.isPro.value) schedules else schedules.take(1)
        if (schedulesToActivate.isNotEmpty()) {
            AlarmHelpers.scheduleAll(context, schedulesToActivate)
        }
    }
}
