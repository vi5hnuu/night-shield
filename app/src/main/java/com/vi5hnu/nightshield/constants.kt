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
    private const val KEY_SHAKE_INTENSITY = "shake_intensity"
    private const val KEY_GRADUAL_FADE = "gradual_fade_enabled"
    private const val KEY_APP_THEME = "app_theme"
    private const val KEY_WIDGET_STYLE = "widget_style"
    private const val KEY_SCHEDULES = "schedules_v2"
    private const val KEY_APP_CONFIGS = "app_filter_configs"
    private const val KEY_PROFILES = "filter_profiles"
    private const val KEY_FIRST_LAUNCH_DAY = "first_launch_epoch_day"
    private const val KEY_LAST_ACTIVE_DAY  = "last_active_epoch_day"
    private const val KEY_STREAK_DAYS      = "streak_days"
    private const val KEY_UPGRADE_PROMPT_SHOWN = "upgrade_prompt_shown"
    private const val KEY_SLEEP_TIMER_END_MS    = "sleep_timer_end_ms"
    private const val KEY_EYE_BREAK_ENABLED          = "eye_break_enabled"
    private const val KEY_DARK_MODE_SYNC             = "dark_mode_auto_sync"
    private const val KEY_BATTERY_BANNER_DISMISSED   = "battery_banner_dismissed"

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

    fun saveShakeIntensity(context: Context, intensity: NightShieldManager.ShakeIntensity) {
        context.appPrefs().edit { putString(KEY_SHAKE_INTENSITY, intensity.name) }
    }

    fun loadShakeIntensity(context: Context): NightShieldManager.ShakeIntensity {
        val name = context.appPrefs().getString(KEY_SHAKE_INTENSITY, null)
        return runCatching {
            NightShieldManager.ShakeIntensity.valueOf(name ?: "")
        }.getOrDefault(NightShieldManager.ShakeIntensity.NORMAL)
    }

    fun saveGradualFade(context: Context, enabled: Boolean) {
        context.appPrefs().edit { putBoolean(KEY_GRADUAL_FADE, enabled) }
    }

    fun loadGradualFade(context: Context): Boolean =
        context.appPrefs().getBoolean(KEY_GRADUAL_FADE, false)

    fun saveAppTheme(context: Context, theme: NightShieldManager.AppTheme) {
        context.appPrefs().edit { putString(KEY_APP_THEME, theme.name) }
    }

    fun loadAppTheme(context: Context): NightShieldManager.AppTheme {
        val name = context.appPrefs().getString(KEY_APP_THEME, null)
        return runCatching {
            NightShieldManager.AppTheme.valueOf(name ?: "")
        }.getOrDefault(NightShieldManager.AppTheme.SYSTEM)
    }

    fun saveWidgetStyle(context: Context, style: NightShieldManager.WidgetStyle) {
        context.appPrefs().edit { putString(KEY_WIDGET_STYLE, style.name) }
    }

    fun loadWidgetStyle(context: Context): NightShieldManager.WidgetStyle {
        val name = context.appPrefs().getString(KEY_WIDGET_STYLE, null)
        return runCatching {
            NightShieldManager.WidgetStyle.valueOf(name ?: "")
        }.getOrDefault(NightShieldManager.WidgetStyle.STANDARD)
    }

    // ── Saved profiles (format: "id|name|colorArgb|intensity") ───────────────

    fun saveProfiles(context: Context, profiles: List<FilterProfile>) {
        val set = profiles.map { "${it.id}|${it.name}|${it.colorArgb}|${it.intensity}" }.toSet()
        context.appPrefs().edit { putStringSet(KEY_PROFILES, set) }
    }

    fun loadProfiles(context: Context): List<FilterProfile> {
        val set = context.appPrefs().getStringSet(KEY_PROFILES, emptySet()) ?: return emptyList()
        return set.mapNotNull { entry ->
            val parts = entry.split("|")
            // format: id | ...name... | colorArgb | intensity
            // id and the last two fields are safe (UUID / Int / Float — never contain '|').
            // Everything in between is the profile name, which can contain '|'.
            if (parts.size < 4) return@mapNotNull null
            runCatching {
                FilterProfile(
                    id       = parts[0],
                    name     = parts.subList(1, parts.size - 2).joinToString("|"),
                    colorArgb = parts[parts.size - 2].toInt(),
                    intensity = parts[parts.size - 1].toFloat(),
                )
            }.getOrNull()
        }.sortedBy { it.name }
    }

    // ── Schedules (serialised as StringSet, format: "id|hour|minute|action|enabled") ──

    // Format: "id|hour|minute|action|enabled|targetIntensity_or_null"
    fun saveSchedules(context: Context, schedules: List<ScheduleEntry>) {
        val set = schedules.map {
            "${it.id}|${it.hour}|${it.minute}|${it.action.name}|${it.enabled}|${it.targetIntensity ?: "null"}"
        }.toSet()
        context.appPrefs().edit { putStringSet(KEY_SCHEDULES, set) }
    }

    fun loadSchedules(context: Context): List<ScheduleEntry> {
        val set = context.appPrefs().getStringSet(KEY_SCHEDULES, emptySet()) ?: return emptyList()
        return set.mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size < 5) return@mapNotNull null
            runCatching {
                val hour   = parts[1].toInt()
                val minute = parts[2].toInt()
                // Reject out-of-range values — Calendar would silently roll them over
                // to the wrong day/time, firing the alarm at an unexpected moment.
                if (hour !in 0..23 || minute !in 0..59) return@mapNotNull null
                ScheduleEntry(
                    id = parts[0],
                    hour = hour,
                    minute = minute,
                    action = ScheduleAction.valueOf(parts[3]),
                    enabled = parts[4].toBooleanStrict(),
                    targetIntensity = if (parts.size >= 6 && parts[5] != "null") parts[5].toFloat() else null,
                )
            }.getOrNull()
        }.sortedWith(compareBy({ it.hour }, { it.minute }))
    }

    // Format: "pkg|appLabel|filterDisabled|customIntensity_or_null|customColorArgb_or_null"
    fun saveAppConfigs(context: Context, configs: Map<String, AppFilterConfig>) {
        val set = configs.values.map {
            "${it.packageName}|${it.appLabel}|${it.filterDisabled}" +
            "|${it.customIntensity ?: "null"}|${it.customColor?.toArgb() ?: "null"}"
        }.toSet()
        context.appPrefs().edit { putStringSet(KEY_APP_CONFIGS, set) }
    }

    fun loadAppConfigs(context: Context): Map<String, AppFilterConfig> {
        val set = context.appPrefs().getStringSet(KEY_APP_CONFIGS, emptySet()) ?: return emptyMap()
        return set.mapNotNull { entry ->
            val parts = entry.split("|")
            // format: packageName | ...appLabel... | filterDisabled | customIntensity | customColor
            // packageName (no '|'), the last 3 fields are fixed-type (Boolean / Float / Int).
            // Everything in between is appLabel, which can contain '|'.
            if (parts.size < 5) return@mapNotNull null
            if (parts[0].isBlank()) return@mapNotNull null
            runCatching {
                AppFilterConfig(
                    packageName    = parts[0],
                    appLabel       = parts.subList(1, parts.size - 3).joinToString("|"),
                    filterDisabled = parts[parts.size - 3].toBooleanStrict(),
                    customIntensity = parts[parts.size - 2].let { if (it == "null") null else it.toFloat() },
                    customColor    = parts[parts.size - 1].let { if (it == "null") null else Color(it.toInt()) },
                )
            }.getOrNull()
        }.associateBy { it.packageName }
    }

    // ── Streak & engagement tracking ─────────────────────────────────────────

    /** Called once on first launch to record the install epoch day. */
    fun ensureFirstLaunchRecorded(context: Context) {
        val prefs = context.appPrefs()
        if (!prefs.contains(KEY_FIRST_LAUNCH_DAY)) {
            prefs.edit { putLong(KEY_FIRST_LAUNCH_DAY, todayEpochDay()) }
        }
    }

    fun daysSinceFirstLaunch(context: Context): Int {
        val first = context.appPrefs().getLong(KEY_FIRST_LAUNCH_DAY, todayEpochDay())
        return (todayEpochDay() - first).toInt().coerceAtLeast(0)
    }

    /**
     * Updates the streak on every session stop.
     * Consecutive day = last active was exactly yesterday; broken = any larger gap.
     */
    fun updateStreak(context: Context) {
        val prefs  = context.appPrefs()
        val today  = todayEpochDay()
        val lastDay = prefs.getLong(KEY_LAST_ACTIVE_DAY, -1L)
        val streak  = prefs.getInt(KEY_STREAK_DAYS, 0)

        val newStreak = when {
            lastDay == today     -> streak              // already counted today
            lastDay == today - 1 -> streak + 1          // consecutive day
            else                 -> 1                   // gap — restart streak
        }
        prefs.edit {
            putLong(KEY_LAST_ACTIVE_DAY, today)
            putInt(KEY_STREAK_DAYS, newStreak)
        }
    }

    fun getStreakDays(context: Context): Int =
        context.appPrefs().getInt(KEY_STREAK_DAYS, 0)

    fun getTotalFilterMinutes(context: Context): Int =
        UsageTracker.getWeeklyUsage(context).sumOf { it.second }

    fun isUpgradePromptShown(context: Context): Boolean =
        context.appPrefs().getBoolean(KEY_UPGRADE_PROMPT_SHOWN, false)

    fun markUpgradePromptShown(context: Context) =
        context.appPrefs().edit { putBoolean(KEY_UPGRADE_PROMPT_SHOWN, true) }

    // ── Sleep timer persistence ───────────────────────────────────────────────

    /** Save the absolute deadline (epoch ms) of the active sleep timer. */
    fun saveSleepTimerEnd(context: Context, endMs: Long) {
        context.appPrefs().edit { putLong(KEY_SLEEP_TIMER_END_MS, endMs) }
    }

    /** Returns the saved deadline, or 0 if none. */
    fun loadSleepTimerEndMs(context: Context): Long =
        context.appPrefs().getLong(KEY_SLEEP_TIMER_END_MS, 0L)

    fun clearSleepTimer(context: Context) {
        context.appPrefs().edit { remove(KEY_SLEEP_TIMER_END_MS) }
    }

    // ── Eye break reminders ───────────────────────────────────────────────────

    fun saveEyeBreakEnabled(context: Context, enabled: Boolean) {
        context.appPrefs().edit { putBoolean(KEY_EYE_BREAK_ENABLED, enabled) }
    }

    fun loadEyeBreakEnabled(context: Context): Boolean =
        context.appPrefs().getBoolean(KEY_EYE_BREAK_ENABLED, false)

    // ── Dark mode auto-sync ───────────────────────────────────────────────────

    fun saveDarkModeSync(context: Context, enabled: Boolean) {
        context.appPrefs().edit { putBoolean(KEY_DARK_MODE_SYNC, enabled) }
    }

    fun loadDarkModeSync(context: Context): Boolean =
        context.appPrefs().getBoolean(KEY_DARK_MODE_SYNC, false)

    // ── Battery banner dismissal ──────────────────────────────────────────────

    fun saveBatteryBannerDismissed(context: Context) {
        context.appPrefs().edit { putBoolean(KEY_BATTERY_BANNER_DISMISSED, true) }
    }

    fun isBatteryBannerDismissed(context: Context): Boolean =
        context.appPrefs().getBoolean(KEY_BATTERY_BANNER_DISMISSED, false)

    /**
     * Called when Pro is revoked (refund / chargebacked).
     * Resets any Pro-only setting back to a free-tier default —
     * both in SharedPreferences and in the live NightShieldManager state.
     */
    fun enforceFreeLimits(context: Context) {
        // 1. Theme → SYSTEM (all non-SYSTEM themes are Pro-only)
        val currentTheme = loadAppTheme(context)
        if (currentTheme != NightShieldManager.AppTheme.SYSTEM) {
            saveAppTheme(context, NightShieldManager.AppTheme.SYSTEM)
            NightShieldManager.setAppTheme(NightShieldManager.AppTheme.SYSTEM)
        }

        // 2. Widget style → STANDARD (MINIMAL and DETAILED are Pro-only; free default is STANDARD)
        val currentStyle = loadWidgetStyle(context)
        if (currentStyle != NightShieldManager.WidgetStyle.STANDARD) {
            saveWidgetStyle(context, NightShieldManager.WidgetStyle.STANDARD)
            NightShieldManager.setWidgetStyle(NightShieldManager.WidgetStyle.STANDARD)
        }

        // 3. Gradual fade → off (Pro-only feature)
        if (loadGradualFade(context)) {
            saveGradualFade(context, false)
            NightShieldManager.setGradualFadeEnabled(false)
        }

        // 4. Schedules → keep at most 1 (free limit)
        val schedules = loadSchedules(context)
        if (schedules.size > 1) {
            val trimmed = schedules.take(1)
            saveSchedules(context, trimmed)
            NightShieldManager.setSchedules(trimmed)
            AlarmHelpers.scheduleAll(context, trimmed)
        }

        // 5. Per-app custom colors → stripped (custom color is Pro-only)
        val appConfigs = loadAppConfigs(context)
        if (appConfigs.values.any { it.customColor != null }) {
            val stripped = appConfigs.mapValues { (_, cfg) -> cfg.copy(customColor = null) }
            saveAppConfigs(context, stripped)
            NightShieldManager.setAppFilterConfigs(stripped)
        }

        // 6. Update widget to reflect new style
        NightShieldWidgetProvider.updateWidget(context)
    }

    private fun todayEpochDay(): Long =
        java.time.LocalDate.now().toEpochDay()

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
        val pendingIntent = buildPendingIntentWithExtras(context, requestCode, entry)
        val millis = nextMillis(entry.hour, entry.minute)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
    }

    /** Like [buildPendingIntent] but also encodes optional extras from the entry. */
    private fun buildPendingIntentWithExtras(context: Context, requestCode: Int, entry: ScheduleEntry): PendingIntent {
        val actionStr = when (entry.action) {
            ScheduleAction.ON      -> OverlayAlarmReceiver.ACTION_START
            ScheduleAction.OFF     -> OverlayAlarmReceiver.ACTION_STOP
            ScheduleAction.SUNRISE -> OverlayAlarmReceiver.ACTION_SUNRISE
        }
        val intent = Intent(context, OverlayAlarmReceiver::class.java).apply {
            putExtra(OverlayAlarmReceiver.EXTRA_ACTION, actionStr)
            entry.targetIntensity?.let { putExtra(OverlayAlarmReceiver.EXTRA_INTENSITY, it) }
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelAll(context: Context, count: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        repeat(count) { i ->
            ScheduleAction.entries.forEach { action ->
                am.cancel(buildPendingIntent(context, RC_BASE + i, action))
            }
        }
    }

    private fun buildPendingIntent(context: Context, requestCode: Int, action: ScheduleAction): PendingIntent {
        val actionStr = when (action) {
            ScheduleAction.ON      -> OverlayAlarmReceiver.ACTION_START
            ScheduleAction.OFF     -> OverlayAlarmReceiver.ACTION_STOP
            ScheduleAction.SUNRISE -> OverlayAlarmReceiver.ACTION_SUNRISE
        }
        val intent = Intent(context, OverlayAlarmReceiver::class.java).apply {
            putExtra(OverlayAlarmReceiver.EXTRA_ACTION, actionStr)
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
