package com.vi5hnu.nightshield

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.vi5hnu.nightshield.ui.theme.NightShieldTheme
import com.vi5hnu.nightshield.widgets.ColorPicker
import com.vi5hnu.nightshield.widgets.FilterOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch


class NightShieldService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val _lifecycleRegistry = LifecycleRegistry(this)
    private val _savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = _savedStateRegistryController.savedStateRegistry
    override val lifecycle: Lifecycle = _lifecycleRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var notificationManager: NotificationManager
    private var overlayView: ComposeView? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var sleepTimerJob: Job? = null
    private var sunriseJob: Job? = null
    private var fadeJob: Job? = null
    private var eyeBreakJob: Job? = null
    private var shakeHelper: ShakeHelper? = null
    /** Absolute deadline (epoch ms) of the active sleep timer; 0 = none. */
    private var sleepTimerEndMs = 0L

    // Adaptive intensity (ambient light)
    private val sensorManager by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }
    private val lightSensor by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) }
    private var lightRegistered = false
    private var lastLuxUpdateMs = 0L
    private val lightListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val now = System.currentTimeMillis()
            if (now - lastLuxUpdateMs < 1500L) return   // throttle
            lastLuxUpdateMs = now
            // Dark room → full strength (1.0); bright (≥200 lux) → floor (0.35).
            val t = (event.values[0] / 200f).coerceIn(0f, 1f)
            NightShieldManager.setAdaptiveMultiplier(1f - t * (1f - 0.35f))
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        _savedStateRegistryController.performAttach()
        _savedStateRegistryController.performRestore(null)
        _lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        UsageTracker.recordStart()
        bootstrapManagerIfNeeded()
        // Claim the active state now (also covers START_STICKY restarts, which re-run onCreate
        // with intent=null in onStartCommand). NOTE: the shake monitor is deliberately NOT stopped
        // here — it is stopped only AFTER startForeground() succeeds (see onStartCommand), so the
        // app keeps the foreground-service state that grants the background-start exemption while
        // this service promotes itself. Stopping the monitor first dropped that exemption and
        // intermittently got startForeground() denied (widget flips ON but the overlay never sticks).
        OverlayHelpers.setOverlaysActive(applicationContext, true)
        NightShieldManager.setFilterActive(true)

        // Shake-to-OFF while the filter is on (continuous-accelerometer Seismic detector).
        // onDestroy is the single writer that clears the active flag, refreshes the widget,
        // and restarts the shake monitor.
        shakeHelper = ShakeHelper(this) {
            if (NightShieldManager.allowShake.value) {
                NightShieldManager.tryShakeToggle {
                    ShakeHelper.hapticFeedback(applicationContext)
                    OverlayHelpers.clearSleepTimer(applicationContext)
                    NightShieldManager.setSleepTimer(0)
                    stopSelf()
                }
            }
        }
        shakeHelper?.start()

        // Update overlay interactivity when color picker opens/closes
        serviceScope.launch {
            NightShieldManager.isCanvasColorPickerVisible.collect { visible ->
                overlayView?.let {
                    val params = it.layoutParams as WindowManager.LayoutParams
                    params.flags = if (visible) NightShieldManager.inactiveFieldFlags
                                   else NightShieldManager.activeFieldFlags
                    windowManager.updateViewLayout(it, params)
                }
            }
        }

        // Sleep timer — persist deadline and update notification every minute
        serviceScope.launch {
            NightShieldManager.sleepTimerMinutes.collect { minutes ->
                sleepTimerJob?.cancel()
                if (minutes > 0) {
                    sleepTimerEndMs = System.currentTimeMillis() + minutes * 60_000L
                    OverlayHelpers.saveSleepTimerEnd(applicationContext, sleepTimerEndMs)
                    updateNotification()
                    sleepTimerJob = launch {
                        while (true) {
                            val remaining = sleepTimerEndMs - System.currentTimeMillis()
                            if (remaining <= 0) {
                                OverlayHelpers.clearSleepTimer(applicationContext)
                                NightShieldManager.setSleepTimer(0)
                                OverlayHelpers.setOverlaysActive(applicationContext, false)
                                NightShieldWidgetProvider.updateWidget(applicationContext)
                                stopSelf()
                                break
                            }
                            delay(60_000L.coerceAtMost(remaining))
                            updateNotification()
                        }
                    }
                } else {
                    sleepTimerEndMs = 0L
                    OverlayHelpers.clearSleepTimer(applicationContext)
                    updateNotification()
                }
            }
        }

        // Eye break reminders — start/stop job when toggle changes
        serviceScope.launch {
            NightShieldManager.eyeBreakEnabled.collect { enabled ->
                eyeBreakJob?.cancel()
                eyeBreakJob = null
                if (enabled) startEyeBreakReminders()
            }
        }

        // Keep the notification + widgets' intensity text in sync when intensity changes
        // (manual slider, widget, ±10% buttons). Debounced via collectLatest+delay so a slider
        // drag (which emits ~60×/s) doesn't spam notify() and hit the ~10/s rate limit.
        serviceScope.launch {
            NightShieldManager.filterIntensity.drop(1).collectLatest {
                delay(400)
                if (isRunning) {
                    updateNotification()
                    NightShieldWidgetProvider.updateWidget(applicationContext)
                    IntensityWidgetProvider.updateAll(applicationContext)
                }
            }
        }

        // Adaptive intensity — register/unregister the light sensor as the toggle changes (Pro).
        serviceScope.launch {
            NightShieldManager.adaptiveIntensity.collect { enabled ->
                if (enabled && ProGate.isPro.value) registerLightSensor() else unregisterLightSensor()
            }
        }
    }

    private fun registerLightSensor() {
        val sensor = lightSensor ?: return
        if (lightRegistered) return
        lightRegistered = true
        lastLuxUpdateMs = 0L
        sensorManager.registerListener(lightListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun unregisterLightSensor() {
        if (lightRegistered) {
            sensorManager.unregisterListener(lightListener)
            lightRegistered = false
        }
        NightShieldManager.setAdaptiveMultiplier(1f)   // return the filter to full strength
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isSunrise = intent?.getBooleanExtra(EXTRA_SUNRISE, false) ?: false

        // Active state was claimed in onCreate (which always runs before onStartCommand, including
        // on START_STICKY restarts). Refresh the widgets here so they reflect ON immediately.
        NightShieldWidgetProvider.updateWidget(applicationContext)
        IntensityWidgetProvider.updateAll(applicationContext)

        showOverlay(isSunrise)
        createNotificationChannel()
        val notification = createNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Foreground promotion was denied (e.g. background-start exemption lost). Abort cleanly
            // so the flag/widget don't get stuck ON; onDestroy clears them and the monitor — which
            // we have NOT stopped yet — stays alive to handle the next shake.
            stopSelf()
            return START_NOT_STICKY
        }

        // Now firmly foreground: safe to stop the shake monitor without dropping the FGS state.
        ShakeMonitorService.stop(applicationContext)

        if (isSunrise && ProGate.isPro.value) startSunriseMode()
        return START_STICKY
    }

    /**
     * PRO — Sunrise Alarm Mode.
     * Starts the filter at full intensity and gradually fades it out to 0 over
     * [SUNRISE_DURATION_MS] (default 30 min), then stops the service.
     * This simulates a natural sunrise: screen gets progressively brighter.
     */
    private fun startSunriseMode() {
        sunriseJob?.cancel()
        val startIntensity = NightShieldManager.filterIntensity.value.coerceAtLeast(0.8f)
        NightShieldManager.setFilterIntensity(startIntensity)
        val startTime = System.currentTimeMillis()
        sunriseJob = serviceScope.launch {
            while (true) {
                // Stop immediately if Pro was revoked mid-animation (e.g., billing refund)
                if (!ProGate.isPro.value) { stopSelf(); break }
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= SUNRISE_DURATION_MS) {
                    OverlayHelpers.setOverlaysActive(applicationContext, false)
                    NightShieldWidgetProvider.updateWidget(applicationContext)
                    stopSelf()
                    break
                }
                val progress = elapsed.toFloat() / SUNRISE_DURATION_MS
                // Interpolate from startIntensity → 0
                NightShieldManager.setFilterIntensity(startIntensity * (1f - progress))
                delay(30_000L)  // update every 30 s — smooth enough, battery-friendly
            }
        }
    }

    /**
     * Drives the PRO gradual fade-in as a service coroutine instead of an in-Compose animation.
     * Must be invoked BEFORE the overlay's first frame so the initial value is already in place
     * (otherwise the tint flashes at full strength before settling). When the filter is activated
     * from a background shake the overlay is created off-screen, where the Compose frame clock may
     * not tick — a self-driven animateFloatAsState would stall at 0 and the filter would never
     * appear. A delay-based StateFlow ramp re-posts a main-looper frame each tick (same proven
     * pattern as [startSunriseMode]).
     */
    private fun startFadeIn(sunrise: Boolean) {
        fadeJob?.cancel()
        // No fade (toggle off) or Sunrise mode (which runs its own brighten-over-30min ramp):
        // render at full strength immediately.
        if (!NightShieldManager.gradualFadeEnabled.value || sunrise) {
            NightShieldManager.setFadeMultiplier(1f)
            return
        }
        NightShieldManager.setFadeMultiplier(0f)
        val startTime = System.currentTimeMillis()
        fadeJob = serviceScope.launch {
            while (true) {
                val progress = ((System.currentTimeMillis() - startTime).toFloat()
                    / FADE_IN_DURATION_MS).coerceIn(0f, 1f)
                NightShieldManager.setFadeMultiplier(progress)
                if (progress >= 1f) break
                delay(FADE_IN_STEP_MS)
            }
        }
    }

    private fun showOverlay(sunrise: Boolean) {
        if (overlayView != null) return
        // Set the fade-in's starting value before the view draws its first frame.
        startFadeIn(sunrise)
        _lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        _lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@NightShieldService)
            setViewTreeSavedStateRegistryOwner(this@NightShieldService)
            setContent {
                NightShieldTheme {
                    Box {
                        val showColorPicker = NightShieldManager.isCanvasColorPickerVisible.collectAsState()

                        FilterOverlay(onTap = {
                            NightShieldManager.setIsCanvasColorPickerVisible(!showColorPicker.value)
                        })

                        if (showColorPicker.value) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                            ) {
                                val color = NightShieldManager.canvasColor.collectAsState()
                                ColorPicker(
                                    initialColor = color.value,
                                    onChange = { NightShieldManager.setCanvasColor(it) },
                                    onDismiss = { NightShieldManager.setIsCanvasColorPickerVisible(false) }
                                )
                            }
                        }
                    }
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            NightShieldManager.activeFieldFlags,
            PixelFormat.TRANSLUCENT
        )
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        try {
            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            // Overlay permission revoked between check and add — bail out cleanly so
            // onDestroy's removeView call doesn't crash on an unattached view.
            overlayView = null
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(channel)
    }

    /** Re-posts the foreground notification with updated intensity/timer text. */
    private fun updateNotification() {
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    // ── Eye break reminders (20-20-20 rule) ────────────────────────────────────

    private fun startEyeBreakReminders() {
        val channel = NotificationChannel(
            EYE_BREAK_CHANNEL_ID,
            "Eye Break Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "20-20-20 rule: look away every 20 minutes" }
        notificationManager.createNotificationChannel(channel)

        eyeBreakJob = serviceScope.launch {
            while (true) {
                delay(20 * 60_000L)
                if (NightShieldManager.eyeBreakEnabled.value) {
                    val note = NotificationCompat.Builder(this@NightShieldService, EYE_BREAK_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_moon_24)
                        .setContentTitle("👁️ Time to rest your eyes!")
                        .setContentText("Look at something 20 feet away for 20 seconds.")
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .build()
                    notificationManager.notify(EYE_BREAK_NOTIFICATION_ID, note)
                }
            }
        }
    }

    private fun createNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, NightShieldWidgetProvider::class.java).apply {
                action = "com.vi5hnu.nightshield.TOGGLE_SHIELD_SIGNAL"
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val intensityPct = (NightShieldManager.filterIntensity.value * 100).toInt()
        val timerSuffix = if (sleepTimerEndMs > 0) {
            val remaining = ((sleepTimerEndMs - System.currentTimeMillis()) / 60_000L).coerceAtLeast(1)
            " · Off in ${remaining}m"
        } else ""
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_24)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText("Intensity $intensityPct%$timerSuffix · ${getString(R.string.notification_text)}")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_notification_24, getString(R.string.notification_action_stop), stopIntent)

        // PRO: quick intensity ±10% buttons directly in the notification
        if (ProGate.isPro.value) {
            val downIntent = PendingIntent.getBroadcast(
                this, 10,
                Intent(this, IntensityActionReceiver::class.java).apply {
                    action = IntensityActionReceiver.ACTION_DECREASE
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val upIntent = PendingIntent.getBroadcast(
                this, 11,
                Intent(this, IntensityActionReceiver::class.java).apply {
                    action = IntensityActionReceiver.ACTION_INCREASE
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_brightness_24, "−10%", downIntent)
            builder.addAction(R.drawable.ic_brightness_24, "+10%", upIntent)
        }
        return builder.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        unregisterLightSensor()
        UsageTracker.recordStop(applicationContext)
        shakeHelper?.stop()
        shakeHelper = null
        sleepTimerJob?.cancel()
        sunriseJob?.cancel()
        fadeJob?.cancel()
        eyeBreakJob?.cancel()
        serviceScope.cancel()
        // Reset so a stale mid-ramp value can't leave the next activation's first frame dimmed.
        NightShieldManager.setFadeMultiplier(1f)
        // Sole writer of active state: clear the persisted flag + reactive flow, then
        // refresh the widget so it shows OFF the moment the service stops.
        OverlayHelpers.dispose(applicationContext)
        NightShieldManager.setFilterActive(false)
        NightShieldWidgetProvider.updateWidget(applicationContext)
        IntensityWidgetProvider.updateAll(applicationContext)
        // Filter is now off — reconcile the shake monitor so shake-to-ON keeps working.
        NightShieldController.syncShakeMonitor(applicationContext)
        _lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        // Wrap in try-catch: if overlay permission was revoked while the service was running,
        // removeView() throws IllegalArgumentException (view not attached), which would abort
        // onDestroy and leave all subsequent cleanup unexecuted.
        try { overlayView?.let { windowManager.removeView(it) } } catch (_: Exception) {}
        overlayView = null
    }

    /** Hydrates [NightShieldManager] from SharedPreferences on a cold process start. */
    private fun bootstrapManagerIfNeeded() {
        // Init billing first so ProGate.isPro is set before any Pro-gated feature is loaded.
        BillingManager.init(applicationContext)

        val (color, intensity, allowShake) = OverlayHelpers.loadFilterSettings(applicationContext)
        if (NightShieldManager.canvasColor.value == NightShieldManager.TemperaturePreset.AMBER.color) {
            // Only override if still at the hardcoded default — means MainActivity never ran
            NightShieldManager.setCanvasColor(color)
            NightShieldManager.setFilterIntensity(intensity)
        }
        NightShieldManager.setAllowShake(allowShake)
        NightShieldManager.setShakeIntensity(OverlayHelpers.loadShakeIntensity(applicationContext))
        NightShieldManager.setGradualFadeEnabled(OverlayHelpers.loadGradualFade(applicationContext) && ProGate.isPro.value)
        NightShieldManager.setDimLevel(OverlayHelpers.loadDimLevel(applicationContext))
        NightShieldManager.setAdaptiveIntensity(OverlayHelpers.loadAdaptiveIntensity(applicationContext) && ProGate.isPro.value)
        NightShieldManager.setEyeBreakEnabled(OverlayHelpers.loadEyeBreakEnabled(applicationContext))
        NightShieldManager.setDarkModeAutoSync(OverlayHelpers.loadDarkModeSync(applicationContext))

        // Restore sleep timer if service was restarted with an active timer
        val savedEnd = OverlayHelpers.loadSleepTimerEndMs(applicationContext)
        if (savedEnd > System.currentTimeMillis()) {
            sleepTimerEndMs = savedEnd
            val remaining = ((savedEnd - System.currentTimeMillis()) / 60_000L).toInt().coerceAtLeast(1)
            NightShieldManager.setSleepTimer(remaining)
        } else if (savedEnd != 0L) {
            // Timer expired while service was down — clear it
            OverlayHelpers.clearSleepTimer(applicationContext)
        }
    }

    companion object {
        /**
         * Process-local liveness flag. True between onCreate and onDestroy of a live instance.
         * Lets callers distinguish "filter flag is true AND the service is actually alive" from
         * "flag is true but the process was killed" — so reconcile-on-resume only restarts the
         * service in the genuine-recovery case, never during the brief stopService→onDestroy gap.
         */
        @Volatile
        var isRunning = false
            private set

        const val EXTRA_SUNRISE = "sunrise_mode"
        private const val SUNRISE_DURATION_MS = 30 * 60 * 1000L  // 30 minutes
        private const val FADE_IN_DURATION_MS = 12_000L          // gradual fade-in over 12 s
        private const val FADE_IN_STEP_MS = 200L                 // ramp update interval
        private const val CHANNEL_ID = "night_shield_service_channel"
        private const val CHANNEL_NAME = "Night Shield Service"
        private const val NOTIFICATION_ID = 1234
        private const val EYE_BREAK_CHANNEL_ID = "night_shield_eye_break"
        private const val EYE_BREAK_NOTIFICATION_ID = 5678
    }
}
