package com.vi5hnu.nightshield

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import androidx.core.net.toUri

object OverlayHelpers {
    private const val NIGHT_SHIELD_SERVICE = "overlays_active"
    private const val OVERLAY_PREFS_NAME = "overlay_prefs"

    fun setOverlaysActive(context: Context, isActive: Boolean) {
        val sharedPreferences = context.applicationContext.getSharedPreferences(OVERLAY_PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit {
            putBoolean(NIGHT_SHIELD_SERVICE, isActive)
        }
    }

    fun areOverlaysActive(context: Context): Boolean {
        val sharedPreferences = context.applicationContext.getSharedPreferences(OVERLAY_PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(NIGHT_SHIELD_SERVICE, false)
    }

    fun checkOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context);
    }

    fun dispose(context: Context){
        val sharedPreferences = context.applicationContext.getSharedPreferences(OVERLAY_PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit {
            remove(NIGHT_SHIELD_SERVICE)
        }
    }

    //////

}


object AlarmHelpers {

    fun scheduleAlarm(context: Context, requestCode: Int, timeInMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, OverlayAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                // Open settings screen to ask user for permission
                val permissionIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = "package:${context.packageName}".toUri()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(permissionIntent)
                return
            }
        }

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
    }

    fun cancelAlarm(context: Context, requestCode: Int) {
        val intent = Intent(context, OverlayAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }
}
