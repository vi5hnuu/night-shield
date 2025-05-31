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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch


class NightShieldService : Service(), LifecycleOwner,
    SavedStateRegistryOwner {
    private val _lifecycleRegistry = LifecycleRegistry(this)
    private val _savedStateRegistryController: SavedStateRegistryController =
        SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry =
        _savedStateRegistryController.savedStateRegistry
    override val lifecycle: Lifecycle = _lifecycleRegistry
    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        _savedStateRegistryController.performAttach()
        _savedStateRegistryController.performRestore(null)
        _lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager


        serviceScope.launch {
            NightShieldManager.isCanvasColorPickerVisible.collect { enabled ->
                overlayView?.let {
                    val params = it.layoutParams as WindowManager.LayoutParams

                    if (enabled) {
                        // Enable overlay interaction (blocks mobile UI)
                        params.flags = NightShieldManager.inactiveFieldFlags;
                    } else {
                        // Allow full mobile UI interaction
                        params.flags = NightShieldManager.activeFieldFlags

                    }

                    windowManager.updateViewLayout(it, params)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showOverlay()
        createNotificationChannel()
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
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
                        val showColorPicker=NightShieldManager.isCanvasColorPickerVisible.collectAsState();

                        FilterOverlay{NightShieldManager.setIsCanvasColorPickerVisible(false)}

                        //show canvas color picker
                        if (showColorPicker.value) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.Center)
                            ) {
                                val color=NightShieldManager.canvasColor.collectAsState()
                                ColorPicker(sliderRange = 0f..0.6f,initialColor = color.value,onChange = {
                                    NightShieldManager.setCanvasColor(color = it)
                                })
                            }
                        }
                    }
                }
            }
        }


        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            getOverlayType(),
            NightShieldManager.activeFieldFlags,
            PixelFormat.TRANSLUCENT
        )
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        windowManager.addView(overlayView, params)
    }

    private fun getOverlayType(): Int {
        return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("nightshield service")
            .setContentText("nightshieldr draw board is actively running")
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel();
        OverlayHelpers.dispose(applicationContext)
        _lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        overlayView?.let { windowManager.removeView(it) }
    }

    companion object {
        private const val CHANNEL_ID = "night_sheild_service_channel"
        private const val CHANNEL_NAME = "Night Sheild Services"

        private const val NOTIFICATION_ID = 1234
    }
}

