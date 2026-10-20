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

/** Cached device location used to compute local sunrise/sunset for the auto-schedule. */
data class AutoLocation(val lat: Double, val lon: Double, val city: String)

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
    private const val KEY_DIM_LEVEL                  = "screen_dim_level"
    private const val KEY_BACKGROUND_SHAKE           = "background_shake"
    private const val KEY_ADAPTIVE_INTENSITY         = "adaptive_intensity"
    private const val KEY_AUTO_SCHEDULE              = "auto_schedule_enabled"
    private const val KEY_GEO_LAT                    = "geo_lat"
    private const val KEY_GEO_LON                    = "geo_lon"
    private const val KEY_GEO_CITY                   = "geo_city"

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

    fun saveDimLevel(context: Context, level: Float) {
        context.appPrefs().edit { putFloat(KEY_DIM_LEVEL, level) }
    }

    fun loadDimLevel(context: Context): Float =
        context.appPrefs().getFloat(KEY_DIM_LEVEL, 0f)

    fun saveBackgroundShake(context: Context, enabled: Boolean) {
        context.appPrefs().edit { putBoolean(KEY_BACKGROUND_SHAKE, enabled) }
    }

    fun loadBackgroundShake(context: Context): Boolean =
        context.appPrefs().getBoolean(KEY_BACKGROUND_SHAKE, true)

    fun saveAdaptiveIntensity(context: Context, enabled: Boolean) {
        context.appPrefs().edit { putBoolean(KEY_ADAPTIVE_INTENSITY, enabled) }
    }

    fun loadAdaptiveIntensity(context: Context): Boolean =
        context.appPrefs().getBoolean(KEY_ADAPTIVE_INTENSITY, false)

    // ── Auto sunset/sunrise schedule + cached location ────────────────────────
    fun saveAutoScheduleEnabled(context: Context, enabled: Boolean) {
        context.appPrefs().edit { putBoolean(KEY_AUTO_SCHEDULE, enabled) }
    }

    fun loadAutoScheduleEnabled(context: Context): Boolean =
        context.appPrefs().getBoolean(KEY_AUTO_SCHEDULE, false)

    fun saveAutoLocation(context: Context, lat: Double, lon: Double, city: String) {
        context.appPrefs().edit {
            putFloat(KEY_GEO_LAT, lat.toFloat())
            putFloat(KEY_GEO_LON, lon.toFloat())
            putString(KEY_GEO_CITY, city)
        }
    }

    /** Cached location, or null if none has been captured yet. */
    fun loadAutoLocation(context: Context): AutoLocation? {
        val p = context.appPrefs()
        if (!p.contains(KEY_GEO_LAT)) return null
        return AutoLocation(
            lat = p.getFloat(KEY_GEO_LAT, 0f).toDouble(),
            lon = p.getFloat(KEY_GEO_LON, 0f).toDouble(),
            city = p.getString(KEY_GEO_CITY, "") ?: "",
        )
    }

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

        // 3b. Adaptive intensity → off (Pro-only feature)
        if (loadAdaptiveIntensity(context)) {
            saveAdaptiveIntensity(context, false)
            NightShieldManager.setAdaptiveIntensity(false)
        }

        // 3c. Auto sunset/sunrise → off (Pro-only feature); cancel its alarms
        if (loadAutoScheduleEnabled(context)) {
            saveAutoScheduleEnabled(context, false)
            NightShieldManager.setAutoScheduleEnabled(false)
            AutoScheduleHelper.reschedule(context)
        }

        // 3d. Follow system dark mode → off (Pro-only feature)
        if (loadDarkModeSync(context)) {
            saveDarkModeSync(context, false)
            NightShieldManager.setDarkModeAutoSync(false)
        }

        // 4. Schedules → keep the free-tier allowance
        val schedules = loadSchedules(context)
        if (schedules.size > FreeTier.MAX_SCHEDULES) {
            val trimmed = FreeTier.activeSchedules(schedules, isPro = false)
            saveSchedules(context, trimmed)
            NightShieldManager.setSchedules(trimmed)
            AlarmHelpers.rearm(context)
        }

        // 5. Per-app custom colors → stripped (custom color is Pro-only)
        val appConfigs = loadAppConfigs(context)
        if (appConfigs.values.any { it.customColor != null }) {
            val stripped = appConfigs.mapValues { (_, cfg) -> cfg.copy(customColor = null) }
            saveAppConfigs(context, stripped)
            NightShieldManager.setAppFilterConfigs(stripped)
        }

        // 6. Update widgets to reflect the new style and the locked intensity controls
        NightShieldWidgetProvider.updateWidget(context)
        IntensityWidgetProvider.updateAll(context)
    }

    private fun todayEpochDay(): Long =
        java.time.LocalDate.now().toEpochDay()

    private fun Context.appPrefs() =
        applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}


object AlarmHelpers {
    private const val RC_BASE = 2000
    /**
     * Request-code slots cleared on every re-arm. Cancelling only "current size + 10" left alarms
     * behind when the list shrank by more than that (e.g. Pro revoked with many schedules).
     */
    private const val MAX_SLOTS = 64

    fun scheduleAll(context: Context, schedules: List<ScheduleEntry>) {
        cancelAll(context, maxOf(MAX_SLOTS, schedules.size))
        schedules.filter { it.enabled }.forEachIndexed { index, entry ->
            scheduleEntry(context, entry, RC_BASE + index)
        }
    }

    /**
     * Arms the persisted schedules (free-tier capped) for their next occurrence.
     *
     * Alarms are one-shot, so this must run after every schedule fires — previously nothing
     * re-armed them and each schedule fired once, then waited until the app was next paused.
     * Reads from prefs so it is correct in a cold receiver process.
     */
    fun rearm(context: Context) {
        scheduleAll(
            context,
            FreeTier.activeSchedules(OverlayHelpers.loadSchedules(context), ProGate.isPro.value),
        )
    }

    /** Persists a schedule edit and arms it immediately, instead of waiting for onPause. */
    fun commit(context: Context, schedules: List<ScheduleEntry>) {
        OverlayHelpers.saveSchedules(context, schedules)
        rearm(context)
    }

    private fun scheduleEntry(context: Context, entry: ScheduleEntry, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntentWithExtras(context, requestCode, entry)
        val millis = nextMillis(entry.hour, entry.minute)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Exact alarms revoked by the user. This runs from onPause, boot and alarm receivers,
            // so it must not launch a Settings screen (it used to — from the background). An
            // inexact alarm still fires, just possibly a few minutes late.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
            return
        }
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
