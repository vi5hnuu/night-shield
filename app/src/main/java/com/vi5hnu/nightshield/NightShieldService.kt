package com.vi5hnu.nightshield

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
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
import kotlinx.coroutines.launch


class NightShieldService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val _lifecycleRegistry = LifecycleRegistry(this)
    private val _savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = _savedStateRegistryController.savedStateRegistry
    override val lifecycle: Lifecycle = _lifecycleRegistry

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var sleepTimerJob: Job? = null
    private var sunriseJob: Job? = null
    private var shakeHelper: ShakeHelper? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        _savedStateRegistryController.performAttach()
        _savedStateRegistryController.performRestore(null)
        _lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        UsageTracker.recordStart()
        bootstrapManagerIfNeeded()

        // Background shake detection — works even when the app is closed
        shakeHelper = ShakeHelper(this) {
            if (NightShieldManager.allowShake.value) {
                NightShieldManager.tryShakeToggle {
                    OverlayHelpers.setOverlaysActive(applicationContext, false)
                    NightShieldManager.setSleepTimer(0)
                    NightShieldWidgetProvider.updateWidget(applicationContext)
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

        // Sleep timer countdown
        serviceScope.launch {
            NightShieldManager.sleepTimerMinutes.collect { minutes ->
                sleepTimerJob?.cancel()
                if (minutes > 0) {
                    sleepTimerJob = launch {
                        delay(minutes * 60 * 1000L)
                        OverlayHelpers.setOverlaysActive(applicationContext, false)
                        NightShieldManager.setSleepTimer(0)
                        stopSelf()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isSunrise = intent?.getBooleanExtra(EXTRA_SUNRISE, false) ?: false

        // Gradual fade first-use trial: show it once for free so the user experiences the value
        if (!ProGate.isPro.value && !OverlayHelpers.isFadeTrialDone(this)) {
            NightShieldManager.setGradualFadeEnabled(true)
            OverlayHelpers.markFadeTrialDone(this)
        }

        showOverlay()
        createNotificationChannel()
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
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

    private fun showOverlay() {
        if (overlayView != null) return
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
        windowManager.addView(overlayView, params)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
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
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_24)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText("Intensity $intensityPct% · ${getString(R.string.notification_text)}")
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
        UsageTracker.recordStop(applicationContext)
        shakeHelper?.stop()
        shakeHelper = null
        sleepTimerJob?.cancel()
        sunriseJob?.cancel()
        serviceScope.cancel()
        OverlayHelpers.dispose(applicationContext)
        _lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
    }

    /** Hydrates [NightShieldManager] from SharedPreferences on a cold process start. */
    private fun bootstrapManagerIfNeeded() {
        val (color, intensity, allowShake) = OverlayHelpers.loadFilterSettings(applicationContext)
        if (NightShieldManager.canvasColor.value == NightShieldManager.TemperaturePreset.AMBER.color) {
            // Only override if still at the hardcoded default — means MainActivity never ran
            NightShieldManager.setCanvasColor(color)
            NightShieldManager.setFilterIntensity(intensity)
        }
        NightShieldManager.setAllowShake(allowShake)
        NightShieldManager.setShakeIntensity(OverlayHelpers.loadShakeIntensity(applicationContext))
        NightShieldManager.setGradualFadeEnabled(OverlayHelpers.loadGradualFade(applicationContext))
        BillingManager.init(applicationContext)
    }

    companion object {
        const val EXTRA_SUNRISE = "sunrise_mode"
        private const val SUNRISE_DURATION_MS = 30 * 60 * 1000L  // 30 minutes
        private const val CHANNEL_ID = "night_shield_service_channel"
        private const val CHANNEL_NAME = "Night Shield Service"
        private const val NOTIFICATION_ID = 1234
    }
}
