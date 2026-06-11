package com.vi5hnu.nightshield

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
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
 * Lifecycle:
 *  - Started by [NightShieldService.onDestroy] when the filter turns off
 *  - Started by [BootReceiver] at boot if shake is enabled and filter is off
 *  - Stops itself after successfully triggering [NightShieldService]
 *  - Stopped by [NightShieldService.onCreate] when the filter turns on
 */
class ShakeMonitorService : Service() {

    private var shakeHelper: ShakeHelper? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        bootstrapIfNeeded()

        shakeHelper = ShakeHelper(this) {
            if (!NightShieldManager.allowShake.value) { stopSelf(); return@ShakeHelper }
            NightShieldManager.tryShakeToggle {
                ShakeHelper.hapticFeedback(applicationContext)
                if (OverlayHelpers.checkOverlayPermission(applicationContext)) {
                    try {
                        OverlayHelpers.setOverlaysActive(applicationContext, true)
                        startForegroundService(
                            Intent(applicationContext, NightShieldService::class.java)
                        )
                        NightShieldWidgetProvider.updateWidget(applicationContext)
                    } catch (_: Exception) {
                        OverlayHelpers.setOverlaysActive(applicationContext, false)
                    }
                }
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
         * Starts the shake monitor if shake is enabled and the filter is currently off.
         * Reads directly from SharedPreferences so it works in cold-process contexts
         * (BootReceiver, onDestroy) where NightShieldManager may not be hydrated.
         */
        fun startIfNeeded(context: Context) {
            val (_, _, allowShake) = OverlayHelpers.loadFilterSettings(context)
            if (!allowShake) return
            if (OverlayHelpers.areOverlaysActive(context)) return
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
