package com.vi5hnu.nightshield

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.ZoneId

/**
 * Drives the PRO "Auto sunset/sunrise" schedule: turns the filter ON at sunset and OFF at sunrise
 * for the user's cached location. Times shift daily, so a recompute alarm at ~00:05 reschedules
 * each night; [reschedule] is also called on boot and app launch.
 *
 * Uses its own request-code range so it never collides with the user's manual [AlarmHelpers]
 * schedules, and reuses [OverlayAlarmReceiver] (ACTION_START/ACTION_STOP) to actually toggle.
 */
object AutoScheduleHelper {

    private const val RC_ON = 3001
    private const val RC_OFF = 3002
    private const val RC_RECOMPUTE = 3003

    /**
     * Cancel and (if enabled + Pro + located) re-arm the next sunset-ON, sunrise-OFF and the daily
     * recompute. When [activateIfNight] is true (the moment the user enables it) and it's currently
     * night, the filter is switched on immediately.
     */
    fun reschedule(context: Context, activateIfNight: Boolean = false) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(receiverIntent(context, RC_ON, OverlayAlarmReceiver.ACTION_START))
        am.cancel(receiverIntent(context, RC_OFF, OverlayAlarmReceiver.ACTION_STOP))
        am.cancel(receiverIntent(context, RC_RECOMPUTE, OverlayAlarmReceiver.ACTION_AUTO_RECOMPUTE))

        if (!OverlayHelpers.loadAutoScheduleEnabled(context) || !ProGate.isPro.value) return
        val loc = OverlayHelpers.loadAutoLocation(context) ?: return

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val nowMs = System.currentTimeMillis()

        nextEvent(loc, today, nowMs, sunset = true)?.let {
            setExact(context, am, it, RC_ON, OverlayAlarmReceiver.ACTION_START)
        }
        nextEvent(loc, today, nowMs, sunset = false)?.let {
            setExact(context, am, it, RC_OFF, OverlayAlarmReceiver.ACTION_STOP)
        }

        // Daily recompute just after midnight to roll the times forward.
        val recomputeAt = today.plusDays(1).atTime(0, 5).atZone(zone).toInstant().toEpochMilli()
        setExact(context, am, recomputeAt, RC_RECOMPUTE, OverlayAlarmReceiver.ACTION_AUTO_RECOMPUTE)

        if (activateIfNight) {
            val t = SunTimes.compute(loc.lat, loc.lon, today)
            val isNight = t == null /* polar */ || nowMs < t.first || nowMs > t.second
            if (isNight && !NightShieldController.isActive(context)) NightShieldController.activate(context)
        }
    }

    /** Earliest future sunset (or sunrise) epoch-ms, scanning a few days for polar edge cases. */
    private fun nextEvent(loc: AutoLocation, today: LocalDate, nowMs: Long, sunset: Boolean): Long? {
        for (d in 0..3) {
            val t = SunTimes.compute(loc.lat, loc.lon, today.plusDays(d.toLong())) ?: continue
            val ms = if (sunset) t.second else t.first
            if (ms > nowMs) return ms
        }
        return null
    }

    private fun setExact(context: Context, am: AlarmManager, atMs: Long, rc: Int, action: String) {
        // Exact alarms are already used by the manual schedules; mirror that capability handling.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.set(AlarmManager.RTC_WAKEUP, atMs, receiverIntent(context, rc, action))
            return
        }
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, receiverIntent(context, rc, action))
    }

    private fun receiverIntent(context: Context, rc: Int, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, rc,
            Intent(context, OverlayAlarmReceiver::class.java)
                .putExtra(OverlayAlarmReceiver.EXTRA_ACTION, action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
