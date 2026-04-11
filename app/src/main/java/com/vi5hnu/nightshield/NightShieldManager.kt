package com.vi5hnu.nightshield

import android.view.WindowManager
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.*
import java.util.UUID

// ── Schedule model ────────────────────────────────────────────────────────────

enum class ScheduleAction { ON, OFF, SUNRISE }

data class ScheduleEntry(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int,
    val minute: Int,
    val action: ScheduleAction,
    val enabled: Boolean = true,
    /** PRO — for ON action: set filter to this intensity when schedule fires. */
    val targetIntensity: Float? = null,
) {
    val timeString: String get() = "%02d:%02d".format(hour, minute)
}

// ── Per-app filter model ──────────────────────────────────────────────────────

data class AppFilterConfig(
    val packageName: String,
    val appLabel: String = packageName,
    val filterDisabled: Boolean = false,
    val customIntensity: Float? = null,   // null = use global intensity
    /** PRO — override filter color for this specific app. */
    val customColor: androidx.compose.ui.graphics.Color? = null,
)

// ── Saved profile model ───────────────────────────────────────────────────────

/** PRO — A named filter preset that can be saved and recalled instantly. */
data class FilterProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** Stored as ARGB Int to survive serialization without Compose dependency. */
    val colorArgb: Int,
    val intensity: Float,
)

// ── Manager ───────────────────────────────────────────────────────────────────

object NightShieldManager {

    val activeFieldFlags = (
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            or WindowManager.LayoutParams.FLAG_FULLSCREEN
            or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
            or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        )

    val inactiveFieldFlags = (
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            or WindowManager.LayoutParams.FLAG_FULLSCREEN
            or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
            or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        )

    // ── Shake ─────────────────────────────────────────────────────────────────

    /**
     * Controls how sensitive the shake detection is.
     * GENTLE = easiest to trigger (low threshold, short duration)
     * NORMAL = default
     * FIRM   = hardest to trigger (high threshold, long sustained shake)
     */
    enum class ShakeIntensity(
        val threshold: Float,
        val durationMs: Int,
        val label: String,
    ) {
        GENTLE(6f,  500,  "Gentle"),
        NORMAL(10f, 800,  "Normal"),
        FIRM(15f,   1200, "Firm"),
    }

    private val _allowShake = MutableStateFlow(true)
    val allowShake: StateFlow<Boolean> = _allowShake.asStateFlow()
    fun setAllowShake(value: Boolean) { _allowShake.value = value }

    private val _shakeIntensity = MutableStateFlow(ShakeIntensity.NORMAL)
    val shakeIntensity: StateFlow<ShakeIntensity> = _shakeIntensity.asStateFlow()
    fun setShakeIntensity(value: ShakeIntensity) { _shakeIntensity.value = value }

    // Debounce so two services don't double-trigger the same shake event
    private var lastShakeToggleMs = 0L
    fun tryShakeToggle(action: () -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastShakeToggleMs > 2000L) {
            lastShakeToggleMs = now
            action()
        }
    }

    // ── Color picker visibility ───────────────────────────────────────────────
    private val _isCanvasColorPickerVisible = MutableStateFlow(false)
    val isCanvasColorPickerVisible: StateFlow<Boolean> = _isCanvasColorPickerVisible.asStateFlow()
    fun setIsCanvasColorPickerVisible(visible: Boolean) { _isCanvasColorPickerVisible.value = visible }

    // ── Filter color + active preset ──────────────────────────────────────────
    private val _canvasColor = MutableStateFlow(TemperaturePreset.AMBER.color)
    val canvasColor: StateFlow<Color> = _canvasColor.asStateFlow()

    private val _activePreset = MutableStateFlow<TemperaturePreset?>(TemperaturePreset.AMBER)
    val activePreset: StateFlow<TemperaturePreset?> = _activePreset.asStateFlow()

    fun setCanvasColor(color: Color) {
        _canvasColor.value = color
        _activePreset.value = null          // manual color clears preset selection
    }

    fun applyTemperaturePreset(preset: TemperaturePreset) {
        _canvasColor.value = preset.color
        _activePreset.value = preset
    }

    // ── Filter intensity ──────────────────────────────────────────────────────
    private val _filterIntensity = MutableStateFlow(0.6f)
    val filterIntensity: StateFlow<Float> = _filterIntensity.asStateFlow()
    fun setFilterIntensity(intensity: Float) { _filterIntensity.value = intensity.coerceIn(0.1f, 1.0f) }

    // ── Temporarily disabled (per-app accessibility override) ─────────────────
    private val _filterTemporarilyDisabled = MutableStateFlow(false)
    val filterTemporarilyDisabled: StateFlow<Boolean> = _filterTemporarilyDisabled.asStateFlow()
    fun setFilterTemporarilyDisabled(disabled: Boolean) { _filterTemporarilyDisabled.value = disabled }

    // ── Sleep timer ───────────────────────────────────────────────────────────
    private val _sleepTimerMinutes = MutableStateFlow(0)  // 0 = off
    val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes.asStateFlow()
    fun setSleepTimer(minutes: Int) { _sleepTimerMinutes.value = minutes }

    // ── Schedules (multiple entries, sorted by time) ──────────────────────────
    private val _schedules = MutableStateFlow<List<ScheduleEntry>>(emptyList())
    val schedules: StateFlow<List<ScheduleEntry>> = _schedules.asStateFlow()

    fun addSchedule(entry: ScheduleEntry) {
        _schedules.value = (_schedules.value + entry)
            .sortedWith(compareBy({ it.hour }, { it.minute }))
    }

    fun removeSchedule(id: String) {
        _schedules.value = _schedules.value.filter { it.id != id }
    }

    fun toggleScheduleEnabled(id: String) {
        _schedules.value = _schedules.value.map {
            if (it.id == id) it.copy(enabled = !it.enabled) else it
        }
    }

    fun setSchedules(entries: List<ScheduleEntry>) {
        _schedules.value = entries.sortedWith(compareBy({ it.hour }, { it.minute }))
    }

    // ── Per-app filter configs ─────────────────────────────────────────────────
    private val _appFilterConfigs = MutableStateFlow<Map<String, AppFilterConfig>>(emptyMap())
    val appFilterConfigs: StateFlow<Map<String, AppFilterConfig>> = _appFilterConfigs.asStateFlow()

    fun setAppFilterConfig(config: AppFilterConfig) {
        _appFilterConfigs.value = _appFilterConfigs.value + (config.packageName to config)
    }

    fun removeAppFilterConfig(packageName: String) {
        _appFilterConfigs.value = _appFilterConfigs.value - packageName
    }

    fun setAppFilterConfigs(configs: Map<String, AppFilterConfig>) {
        _appFilterConfigs.value = configs
    }

    // ── PRO: Gradual fade-in ───────────────────────────────────────────────────
    private val _gradualFadeEnabled = MutableStateFlow(false)
    val gradualFadeEnabled: StateFlow<Boolean> = _gradualFadeEnabled.asStateFlow()
    fun setGradualFadeEnabled(enabled: Boolean) { _gradualFadeEnabled.value = enabled }

    // ── PRO: Saved profiles ───────────────────────────────────────────────────
    private val _profiles = MutableStateFlow<List<FilterProfile>>(emptyList())
    val profiles: StateFlow<List<FilterProfile>> = _profiles.asStateFlow()

    fun addProfile(profile: FilterProfile) {
        _profiles.value = _profiles.value + profile
    }

    fun removeProfile(id: String) {
        _profiles.value = _profiles.value.filter { it.id != id }
    }

    fun setProfiles(list: List<FilterProfile>) {
        _profiles.value = list
    }

    // ── PRO: App theme ────────────────────────────────────────────────────────
    enum class AppTheme(val label: String) {
        SYSTEM("System"),
        DARK_OLED("Dark OLED"),
        WARM("Warm"),
        BLUE_NIGHT("Blue Night"),
        FOREST("Forest"),
        PURPLE_NIGHT("Purple Night"),
    }

    private val _appTheme = MutableStateFlow(AppTheme.SYSTEM)
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()
    fun setAppTheme(theme: AppTheme) { _appTheme.value = theme }

    // ── PRO: Widget style ─────────────────────────────────────────────────────
    enum class WidgetStyle(val label: String) {
        STANDARD("Standard"),
        MINIMAL("Minimal"),
        DETAILED("Detailed"),
    }

    private val _widgetStyle = MutableStateFlow(WidgetStyle.STANDARD)
    val widgetStyle: StateFlow<WidgetStyle> = _widgetStyle.asStateFlow()
    fun setWidgetStyle(style: WidgetStyle) { _widgetStyle.value = style }

    // ── Temperature presets ───────────────────────────────────────────────────
    enum class TemperaturePreset(val color: Color, val label: String, val dotColor: Color) {
        MIDNIGHT(Color(0xEE130030),  "Midnight",  Color(0xFF4B0082)),
        DEEP_RED(Color(0xCC8B0000),  "Deep Red",  Color(0xFFFF3B30)),
        SUNSET(Color(0xCCFF4500),    "Sunset",    Color(0xFFFF6B35)),
        AMBER(Color(0x99FFA500),     "Amber",     Color(0xFFFFA500)),
        WARM(Color(0xBBFFCC44),      "Warm",      Color(0xFFFFD700)),
        COOL(Color(0x663ABDE0),      "Cool",      Color(0xFF3ABDE0)),
        FOREST(Color(0x8800C853),    "Forest",    Color(0xFF00C853)),
        LAVENDER(Color(0xAA9C27B0),  "Lavender",  Color(0xFFCE93D8)),
        ROSE(Color(0xAAE91E63),      "Rose",      Color(0xFFF48FB1)),
    }
}
