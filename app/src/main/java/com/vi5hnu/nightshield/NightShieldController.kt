package com.vi5hnu.nightshield

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Single control surface for the night filter.
 *
 * Every external trigger (home button, widget, quick-settings tile, scheduled alarm,
 * Tasker intent, shake) routes through here instead of duplicating the
 * "start service + flip flag + refresh widget" block. This is the only place that
 * issues filter start/stop intents.
 *
 * State ownership:
 *  - This controller does NOT write the persisted active flag ([OverlayHelpers.setOverlaysActive]).
 *    [NightShieldService] is the sole writer — it sets the flag true in onStartCommand and
 *    false in onDestroy, and refreshes the widget on both transitions. Routing all callers
 *    through here (without optimistic flag writes) is what keeps the widget from desyncing.
 *
 * Shake monitor:
 *  - [ShakeMonitorService] must run exactly when the user can shake-to-activate:
 *    shake enabled AND filter off AND overlay permission granted. [syncShakeMonitor]
 *    enforces that single rule and is called from every state-change point.
 */
object NightShieldController {

    /** Durable truth: is the filter currently meant to be on. */
    fun isActive(context: Context): Boolean =
        OverlayHelpers.areOverlaysActive(context)

    /**
     * Start the filter. No-op (returns false) if overlay permission is missing.
     * The service flips the active flag and refreshes the widget itself.
     */
    fun activate(context: Context, sunrise: Boolean = false): Boolean {
        if (!OverlayHelpers.checkOverlayPermission(context)) return false
        val intent = Intent(context, NightShieldService::class.java).apply {
            if (sunrise) putExtra(NightShieldService.EXTRA_SUNRISE, true)
        }
        return try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (_: Exception) {
            // Background-start denied or service unavailable — leave state untouched.
            false
        }
    }

    /** Stop the filter. The service clears the flag and refreshes the widget in onDestroy. */
    fun deactivate(context: Context) {
        context.stopService(Intent(context, NightShieldService::class.java))
    }

    /** Toggle the filter based on the current persisted state. */
    fun toggle(context: Context) {
        if (isActive(context)) deactivate(context) else activate(context)
    }

    /**
     * Reconcile the shake monitor against the single rule:
     * run iff shake enabled AND filter off AND overlay permission granted.
     * Safe to call from any state-change point (launch, resume, shake toggle, boot,
     * filter on/off) — it starts or stops [ShakeMonitorService] as needed.
     */
    fun syncShakeMonitor(context: Context) {
        val (_, _, allowShake) = OverlayHelpers.loadFilterSettings(context)
        val shouldRun = allowShake &&
            !isActive(context) &&
            OverlayHelpers.checkOverlayPermission(context)
        if (shouldRun) ShakeMonitorService.start(context)
        else ShakeMonitorService.stop(context)
    }
}
