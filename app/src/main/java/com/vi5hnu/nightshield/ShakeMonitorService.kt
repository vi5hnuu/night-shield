package com.vi5hnu.nightshield

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Lightweight foreground service that listens for a shake gesture when the
 * filter is OFF and the app is not in the foreground.
 *
 * Why a foreground service instead of relying on the accessibility service:
 * OEM battery managers (Samsung One UI, Xiaomi MIUI, Oppo ColorOS, etc.)
 * frequently kill background processes — including accessibility services —
 * when the user swipes the app from recents. Foreground services display a
 * visible notification and cannot be silently killed by the OS.
 *
 * Lifecycle is governed by a single rule in [NightShieldController.syncShakeMonitor]:
 * run iff shake enabled AND filter off AND overlay permission granted. That sync is
 * called from app launch/resume, the shake toggle, boot, and on every filter on/off
 * transition. The monitor also stops itself immediately after triggering the filter.
 */
class ShakeMonitorService : Service() {

    private var shakeHelper: ShakeHelper? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        bootstrapIfNeeded()

        // Foreground service → unrestricted sensor access, so use the continuous
        // accelerometer directly rather than the fragile significant-motion path.
        shakeHelper = ShakeHelper(this, forceContinuousAccel = true) {
            if (!NightShieldManager.allowShake.value) { stopSelf(); return@ShakeHelper }
            NightShieldManager.tryShakeToggle {
                ShakeHelper.hapticFeedback(applicationContext)
                // Controller starts NightShieldService, which flips the active flag and
                // refreshes the widget. NightShieldService.onCreate stops this monitor.
                NightShieldController.activate(applicationContext)
                stopSelf()
            }
        }
        shakeHelper?.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        shakeHelper?.stop()
        shakeHelper = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun bootstrapIfNeeded() {
        BillingManager.init(applicationContext)
        val (_, _, allowShake) = OverlayHelpers.loadFilterSettings(applicationContext)
        NightShieldManager.setAllowShake(allowShake)
        NightShieldManager.setShakeIntensity(OverlayHelpers.loadShakeIntensity(applicationContext))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Shake Detection",
            NotificationManager.IMPORTANCE_MIN
        ).apply { setShowBadge(false) }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_24)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Shake to activate night filter")
            .setSilent(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

    companion object {
        private const val CHANNEL_ID = "night_shield_shake_monitor"
        const val NOTIFICATION_ID = 5432

        /**
         * Start the monitor. Callers must gate on the "should run" rule first —
         * use [NightShieldController.syncShakeMonitor], which is the single owner of that rule.
         */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ShakeMonitorService::class.java)
                )
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ShakeMonitorService::class.java))
        }
    }
}
