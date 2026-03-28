package com.vi5hnu.nightshield

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import androidx.core.net.toUri

object OverlayHelpers {
    private const val PREFS = "overlay_prefs"
    private const val KEY_ACTIVE = "overlays_active"
    private const val KEY_COLOR = "canvas_color"
    private const val KEY_INTENSITY = "filter_intensity"
    private const val KEY_ALLOW_SHAKE = "allow_shake"
    private const val KEY_SCHEDULES = "schedules_v2"
    private const val KEY_APP_CONFIGS = "app_filter_configs"

    fun setOverlaysActive(context: Context, isActive: Boolean) {
        context.appPrefs().edit { putBoolean(KEY_ACTIVE, isActive) }
    }

    fun areOverlaysActive(context: Context): Boolean =
        context.appPrefs().getBoolean(KEY_ACTIVE, false)

    fun checkOverlayPermission(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun dispose(context: Context) {
        context.appPrefs().edit { remove(KEY_ACTIVE) }
    }

    // ── Filter settings ───────────────────────────────────────────────────────

    fun saveFilterSettings(context: Context, color: Color, intensity: Float, allowShake: Boolean) {
        context.appPrefs().edit {
            putInt(KEY_COLOR, color.toArgb())
            putFloat(KEY_INTENSITY, intensity)
            putBoolean(KEY_ALLOW_SHAKE, allowShake)
        }
    }

    fun loadFilterSettings(context: Context): Triple<Color, Float, Boolean> {
        val p = context.appPrefs()
        return Triple(
            Color(p.getInt(KEY_COLOR, NightShieldManager.TemperaturePreset.AMBER.color.toArgb())),
            p.getFloat(KEY_INTENSITY, 0.6f),
            p.getBoolean(KEY_ALLOW_SHAKE, true)
        )
    }

    // ── Schedules (serialised as StringSet, format: "id|hour|minute|action|enabled") ──

    fun saveSchedules(context: Context, schedules: List<ScheduleEntry>) {
        val set = schedules.map { "${it.id}|${it.hour}|${it.minute}|${it.action.name}|${it.enabled}" }.toSet()
        context.appPrefs().edit { putStringSet(KEY_SCHEDULES, set) }
    }

    fun loadSchedules(context: Context): List<ScheduleEntry> {
        val set = context.appPrefs().getStringSet(KEY_SCHEDULES, emptySet()) ?: return emptyList()
        return set.mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size < 5) return@mapNotNull null
            runCatching {
                ScheduleEntry(
                    id = parts[0],
                    hour = parts[1].toInt(),
                    minute = parts[2].toInt(),
                    action = ScheduleAction.valueOf(parts[3]),
                    enabled = parts[4].toBooleanStrict()
                )
            }.getOrNull()
        }.sortedWith(compareBy({ it.hour }, { it.minute }))
    }

    // ── Per-app configs (format: "pkg|disabled|intensity_or_null") ──────────────

    fun saveAppConfigs(context: Context, configs: Map<String, AppFilterConfig>) {
        val set = configs.values.map {
            "${it.packageName}|${it.appLabel}|${it.filterDisabled}|${it.customIntensity ?: "null"}"
        }.toSet()
        context.appPrefs().edit { putStringSet(KEY_APP_CONFIGS, set) }
    }

    fun loadAppConfigs(context: Context): Map<String, AppFilterConfig> {
        val set = context.appPrefs().getStringSet(KEY_APP_CONFIGS, emptySet()) ?: return emptyMap()
        return set.mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size < 4) return@mapNotNull null
            runCatching {
                AppFilterConfig(
                    packageName = parts[0],
                    appLabel = parts[1],
                    filterDisabled = parts[2].toBooleanStrict(),
                    customIntensity = if (parts[3] == "null") null else parts[3].toFloat()
                )
            }.getOrNull()
        }.associateBy { it.packageName }
    }

    private fun Context.appPrefs() =
        applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}


object AlarmHelpers {
    private const val RC_BASE = 2000

    fun scheduleAll(context: Context, schedules: List<ScheduleEntry>) {
        cancelAll(context, schedules.size + 10)
        schedules.filter { it.enabled }.forEachIndexed { index, entry ->
            scheduleEntry(context, entry, RC_BASE + index)
        }
    }

    private fun scheduleEntry(context: Context, entry: ScheduleEntry, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = "package:${context.packageName}".toUri()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            return
        }
        val pendingIntent = buildPendingIntent(context, requestCode, entry.action)
        val millis = nextMillis(entry.hour, entry.minute)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
    }

    private fun cancelAll(context: Context, count: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        repeat(count) { i ->
            listOf(ScheduleAction.ON, ScheduleAction.OFF).forEach { action ->
                am.cancel(buildPendingIntent(context, RC_BASE + i, action))
            }
        }
    }

    private fun buildPendingIntent(context: Context, requestCode: Int, action: ScheduleAction): PendingIntent {
        val intent = Intent(context, OverlayAlarmReceiver::class.java).apply {
            putExtra(OverlayAlarmReceiver.EXTRA_ACTION,
                if (action == ScheduleAction.ON) OverlayAlarmReceiver.ACTION_START
                else OverlayAlarmReceiver.ACTION_STOP)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nextMillis(hour: Int, minute: Int): Long {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}
