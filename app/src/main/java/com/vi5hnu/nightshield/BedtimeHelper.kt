package com.vi5hnu.nightshield

import android.content.Context

/**
 * One-tap "Bedtime" routine: applies a warm, strong, dimmed filter and a 30-minute sleep timer,
 * then activates the filter. Bundles existing settings into a single relaxing wind-down action.
 */
object BedtimeHelper {

    private const val SLEEP_TIMER_MIN = 30
    private const val INTENSITY = 0.85f
    private const val DIM = 0.30f

    fun apply(context: Context) {
        NightShieldManager.applyTemperaturePreset(NightShieldManager.TemperaturePreset.WARM)
        NightShieldManager.setFilterIntensity(INTENSITY)
        NightShieldManager.setDimLevel(DIM)
        NightShieldManager.setSleepTimer(SLEEP_TIMER_MIN)

        // Persist so a cold service start (or restart) reflects the bedtime settings.
        OverlayHelpers.saveFilterSettings(
            context,
            NightShieldManager.canvasColor.value,
            NightShieldManager.filterIntensity.value,
            NightShieldManager.allowShake.value,
        )
        OverlayHelpers.saveDimLevel(context, NightShieldManager.dimLevel.value)

        NightShieldController.activate(context)
    }
}
