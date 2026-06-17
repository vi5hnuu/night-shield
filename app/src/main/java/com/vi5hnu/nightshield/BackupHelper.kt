package com.vi5hnu.nightshield

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serialises all Night Shield settings to/from a JSON string for Backup & Restore.
 *
 * Format version 1 — backwards-compatible: unknown keys are silently ignored.
 * New fields added in future versions default to current settings on import.
 */
object BackupHelper {

    private const val VERSION = 1

    // ── Export ────────────────────────────────────────────────────────────────

    fun export(context: Context): String {
        val root = JSONObject()
        root.put("version", VERSION)

        // Filter settings
        val (color, intensity, allowShake) = OverlayHelpers.loadFilterSettings(context)
        root.put("filterColorArgb", color.toArgb())
        root.put("filterIntensity", intensity.toDouble())
        root.put("dimLevel", NightShieldManager.dimLevel.value.toDouble())
        root.put("allowShake", allowShake)
        root.put("backgroundShake", NightShieldManager.backgroundShake.value)
        root.put("shakeIntensity", NightShieldManager.shakeIntensity.value.name)
        root.put("gradualFadeEnabled", NightShieldManager.gradualFadeEnabled.value)
        root.put("adaptiveIntensity", NightShieldManager.adaptiveIntensity.value)
        root.put("autoScheduleEnabled", NightShieldManager.autoScheduleEnabled.value)
        OverlayHelpers.loadAutoLocation(context)?.let {
            root.put("geoLat", it.lat)
            root.put("geoLon", it.lon)
            root.put("geoCity", it.city)
        }
        root.put("eyeBreakEnabled", NightShieldManager.eyeBreakEnabled.value)
        root.put("darkModeSync", NightShieldManager.darkModeAutoSync.value)
        root.put("appTheme", NightShieldManager.appTheme.value.name)
        root.put("widgetStyle", NightShieldManager.widgetStyle.value.name)

        // Schedules
        val schedulesArr = JSONArray()
        NightShieldManager.schedules.value.forEach { s ->
            schedulesArr.put(JSONObject().apply {
                put("id", s.id)
                put("hour", s.hour)
                put("minute", s.minute)
                put("action", s.action.name)
                put("enabled", s.enabled)
                put("targetIntensity", s.targetIntensity?.toDouble() ?: JSONObject.NULL)
            })
        }
        root.put("schedules", schedulesArr)

        // Per-app configs
        val appsArr = JSONArray()
        NightShieldManager.appFilterConfigs.value.values.forEach { cfg ->
            appsArr.put(JSONObject().apply {
                put("packageName", cfg.packageName)
                put("appLabel", cfg.appLabel)
                put("filterDisabled", cfg.filterDisabled)
                put("customIntensity", cfg.customIntensity?.toDouble() ?: JSONObject.NULL)
                put("customColorArgb", cfg.customColor?.toArgb() ?: JSONObject.NULL)
            })
        }
        root.put("appConfigs", appsArr)

        // Profiles
        val profilesArr = JSONArray()
        NightShieldManager.profiles.value.forEach { p ->
            profilesArr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("colorArgb", p.colorArgb)
                put("intensity", p.intensity.toDouble())
            })
        }
        root.put("profiles", profilesArr)

        return root.toString(2)
    }

    // ── Import ────────────────────────────────────────────────────────────────

    /**
     * Returns true on success, false if the JSON is malformed or incompatible.
     * Applies changes to [NightShieldManager] and persists via [OverlayHelpers].
     */
    fun import(context: Context, json: String): Boolean = runCatching {
        val root = JSONObject(json)

        // Filter settings
        val colorArgb = root.optInt("filterColorArgb", NightShieldManager.TemperaturePreset.AMBER.color.toArgb())
        val intensity = root.optDouble("filterIntensity", 0.6).toFloat().coerceIn(0.1f, 1.0f)
        val dimLevel = root.optDouble("dimLevel", 0.0).toFloat().coerceIn(0f, 0.85f)
        val allowShake = root.optBoolean("allowShake", true)
        val backgroundShake = root.optBoolean("backgroundShake", true)
        val shakeIntensityName = root.optString("shakeIntensity", NightShieldManager.ShakeIntensity.NORMAL.name)
        val gradualFade = root.optBoolean("gradualFadeEnabled", false)
        val adaptiveIntensity = root.optBoolean("adaptiveIntensity", false)
        val autoSchedule = root.optBoolean("autoScheduleEnabled", false)
        if (root.has("geoLat")) {
            OverlayHelpers.saveAutoLocation(
                context,
                root.optDouble("geoLat", 0.0),
                root.optDouble("geoLon", 0.0),
                root.optString("geoCity", ""),
            )
        }
        val eyeBreakEnabled = root.optBoolean("eyeBreakEnabled", false)
        val darkModeSync = root.optBoolean("darkModeSync", false)
        val themeName = root.optString("appTheme", NightShieldManager.AppTheme.SYSTEM.name)
        val widgetStyleName = root.optString("widgetStyle", NightShieldManager.WidgetStyle.STANDARD.name)

        NightShieldManager.setCanvasColor(androidx.compose.ui.graphics.Color(colorArgb))
        NightShieldManager.setFilterIntensity(intensity)
        NightShieldManager.setDimLevel(dimLevel)
        NightShieldManager.setAllowShake(allowShake)
        NightShieldManager.setBackgroundShake(backgroundShake)
        NightShieldManager.setShakeIntensity(
            runCatching { NightShieldManager.ShakeIntensity.valueOf(shakeIntensityName) }
                .getOrDefault(NightShieldManager.ShakeIntensity.NORMAL)
        )
        NightShieldManager.setGradualFadeEnabled(gradualFade && ProGate.isPro.value)
        NightShieldManager.setAdaptiveIntensity(adaptiveIntensity && ProGate.isPro.value)
        NightShieldManager.setAutoScheduleEnabled(autoSchedule && ProGate.isPro.value)
        NightShieldManager.setAutoCity(OverlayHelpers.loadAutoLocation(context)?.city ?: "")
        NightShieldManager.setEyeBreakEnabled(eyeBreakEnabled)
        NightShieldManager.setDarkModeAutoSync(darkModeSync)
        NightShieldManager.setAppTheme(
            runCatching { NightShieldManager.AppTheme.valueOf(themeName) }
                .getOrDefault(NightShieldManager.AppTheme.SYSTEM)
        )
        NightShieldManager.setWidgetStyle(
            runCatching { NightShieldManager.WidgetStyle.valueOf(widgetStyleName) }
                .getOrDefault(NightShieldManager.WidgetStyle.STANDARD)
        )

        // Schedules — free tier capped at 1; Pro backup imported by free user must be trimmed
        val schedArr = root.optJSONArray("schedules")
        if (schedArr != null) {
            val schedules = (0 until schedArr.length()).mapNotNull { i ->
                runCatching {
                    val o = schedArr.getJSONObject(i)
                    ScheduleEntry(
                        id = o.getString("id"),
                        hour = o.getInt("hour"),
                        minute = o.getInt("minute"),
                        action = ScheduleAction.valueOf(o.getString("action")),
                        enabled = o.getBoolean("enabled"),
                        targetIntensity = if (o.isNull("targetIntensity")) null
                                          else o.getDouble("targetIntensity").toFloat().coerceIn(0.1f, 1.0f),
                    )
                }.getOrNull()
            }.let { list ->
                if (ProGate.isPro.value) list
                // Strip targetIntensity (Pro-only) from the one schedule free users keep
                else list.take(1).map { s -> s.copy(targetIntensity = null) }
            }
            NightShieldManager.setSchedules(schedules)
        }

        // Per-app configs — custom color is Pro-only; strip it on import for free users
        val appsArr = root.optJSONArray("appConfigs")
        if (appsArr != null) {
            val configs = (0 until appsArr.length()).mapNotNull { i ->
                runCatching {
                    val o = appsArr.getJSONObject(i)
                    AppFilterConfig(
                        packageName = o.getString("packageName"),
                        appLabel = o.getString("appLabel"),
                        filterDisabled = o.getBoolean("filterDisabled"),
                        customIntensity = if (o.isNull("customIntensity")) null
                                          else o.getDouble("customIntensity").toFloat().coerceIn(0.1f, 1.0f),
                        customColor = if (!ProGate.isPro.value || o.isNull("customColorArgb")) null
                                      else androidx.compose.ui.graphics.Color(o.getInt("customColorArgb")),
                    )
                }.getOrNull()
            }.associateBy { it.packageName }
            NightShieldManager.setAppFilterConfigs(configs)
        }

        // Profiles
        val profArr = root.optJSONArray("profiles")
        if (profArr != null) {
            val profiles = (0 until profArr.length()).mapNotNull { i ->
                runCatching {
                    val o = profArr.getJSONObject(i)
                    FilterProfile(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        colorArgb = o.getInt("colorArgb"),
                        intensity = o.getDouble("intensity").toFloat().coerceIn(0.1f, 1.0f),
                    )
                }.getOrNull()
            }
            NightShieldManager.setProfiles(profiles)
        }

        // Persist everything
        OverlayHelpers.saveFilterSettings(context, NightShieldManager.canvasColor.value, intensity, allowShake)
        OverlayHelpers.saveShakeIntensity(context, NightShieldManager.shakeIntensity.value)
        OverlayHelpers.saveSchedules(context, NightShieldManager.schedules.value)
        OverlayHelpers.saveAppConfigs(context, NightShieldManager.appFilterConfigs.value)
        OverlayHelpers.saveProfiles(context, NightShieldManager.profiles.value)
        OverlayHelpers.saveAppTheme(context, NightShieldManager.appTheme.value)
        OverlayHelpers.saveWidgetStyle(context, NightShieldManager.widgetStyle.value)
        OverlayHelpers.saveGradualFade(context, gradualFade && ProGate.isPro.value)
        OverlayHelpers.saveAdaptiveIntensity(context, adaptiveIntensity && ProGate.isPro.value)
        OverlayHelpers.saveAutoScheduleEnabled(context, autoSchedule && ProGate.isPro.value)
        AutoScheduleHelper.reschedule(context)
        OverlayHelpers.saveDimLevel(context, dimLevel)
        OverlayHelpers.saveBackgroundShake(context, backgroundShake)
        OverlayHelpers.saveEyeBreakEnabled(context, eyeBreakEnabled)
        OverlayHelpers.saveDarkModeSync(context, darkModeSync)

        true
    }.getOrDefault(false)
}
