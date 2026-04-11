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
        root.put("allowShake", allowShake)
        root.put("shakeIntensity", NightShieldManager.shakeIntensity.value.name)
        root.put("gradualFadeEnabled", NightShieldManager.gradualFadeEnabled.value)
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
        val intensity = root.optDouble("filterIntensity", 0.6).toFloat()
        val allowShake = root.optBoolean("allowShake", true)
        val shakeIntensityName = root.optString("shakeIntensity", NightShieldManager.ShakeIntensity.NORMAL.name)
        val gradualFade = root.optBoolean("gradualFadeEnabled", false)
        val themeName = root.optString("appTheme", NightShieldManager.AppTheme.SYSTEM.name)
        val widgetStyleName = root.optString("widgetStyle", NightShieldManager.WidgetStyle.STANDARD.name)

        NightShieldManager.setCanvasColor(androidx.compose.ui.graphics.Color(colorArgb))
        NightShieldManager.setFilterIntensity(intensity)
        NightShieldManager.setAllowShake(allowShake)
        NightShieldManager.setShakeIntensity(
            runCatching { NightShieldManager.ShakeIntensity.valueOf(shakeIntensityName) }
                .getOrDefault(NightShieldManager.ShakeIntensity.NORMAL)
        )
        NightShieldManager.setGradualFadeEnabled(gradualFade)
        NightShieldManager.setAppTheme(
            runCatching { NightShieldManager.AppTheme.valueOf(themeName) }
                .getOrDefault(NightShieldManager.AppTheme.SYSTEM)
        )
        NightShieldManager.setWidgetStyle(
            runCatching { NightShieldManager.WidgetStyle.valueOf(widgetStyleName) }
                .getOrDefault(NightShieldManager.WidgetStyle.STANDARD)
        )

        // Schedules
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
                                          else o.getDouble("targetIntensity").toFloat(),
                    )
                }.getOrNull()
            }
            NightShieldManager.setSchedules(schedules)
        }

        // Per-app configs
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
                                          else o.getDouble("customIntensity").toFloat(),
                        customColor = if (o.isNull("customColorArgb")) null
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
                        intensity = o.getDouble("intensity").toFloat(),
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
        OverlayHelpers.saveGradualFade(context, gradualFade)

        true
    }.getOrDefault(false)
}
