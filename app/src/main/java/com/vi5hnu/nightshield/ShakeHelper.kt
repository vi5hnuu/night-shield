package com.vi5hnu.nightshield

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.sqrt

/**
 * Shake detector that works reliably in both foreground and background processes.
 *
 * Strategy
 * --------
 * When TYPE_SIGNIFICANT_MOTION is available (API 18+, present on virtually all modern devices):
 *   1. Arm the hardware trigger — zero battery cost until motion is detected.
 *   2. On trigger, open a 3 s accelerometer window to verify a deliberate shake, then re-arm.
 *
 * Hardware trigger sensors are NOT throttled in the background (Android docs). This bypasses the
 * core problem: background accelerometer access is limited to 5 Hz on Android 9+ and is blocked
 * entirely by many OEMs when no foreground service is running.
 *
 * Fallback (no significant-motion hardware): always-on accelerometer. This path is fine for
 * foreground services which have unrestricted sensor access regardless of Android version.
 *
 * All sensor work runs on a dedicated HandlerThread — the main thread is never blocked.
 */
class ShakeHelper(
    context: Context,
    private val thresholdProvider: () -> Float = { NightShieldManager.shakeIntensity.value.threshold },
    private val durationProvider: () -> Int    = { NightShieldManager.shakeIntensity.value.durationMs },
    private val onShake: () -> Unit,
) {
    private val appCtx          = context.applicationContext
    private val sensorManager   = appCtx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer   = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val sigMotion       = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

    private val workerThread    = HandlerThread("night-shield-shake").also { it.start() }
    private val workerHandler   = Handler(workerThread.looper)
    private val mainHandler     = Handler(Looper.getMainLooper())

    // Only accessed on workerThread (no volatile needed)
    private var accelRegistered    = false
    private var isShaking          = false
    private var shakeStartMs       = 0L
    private var lastShakeMs        = 0L
    private var lastFireMs         = 0L

    @Volatile private var stopped  = false

    // ── Accelerometer listener (runs on workerThread) ─────────────────────────

    private val accelListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val (x, y, z) = event.values
            val g         = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
            val now       = System.currentTimeMillis()
            val threshold = thresholdProvider()
            val duration  = durationProvider()

            if (g > threshold) {
                if (!isShaking) { shakeStartMs = now; isShaking = true }
                lastShakeMs = now
            }

            if (isShaking) {
                when {
                    now - shakeStartMs >= duration -> {
                        if (now - lastFireMs >= 2000L) {
                            lastFireMs = now
                            mainHandler.post(onShake)
                        }
                        isShaking = false
                        // Trigger mode: close the window and re-arm after a short pause so
                        // residual motion from the same gesture doesn't re-fire.
                        if (sigMotion != null) stopAccelAndRearm(rearmDelayMs = 500L)
                    }
                    // 800 ms gap tolerance — handles the 5 Hz background throttle rate
                    // (200 ms/event × 4 events = 800 ms max expected gap during active shake).
                    now - lastShakeMs > 800L -> isShaking = false
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // ── Significant-motion trigger (delivered on main thread, one-shot) ───────

    private val triggerListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent) {
            if (!stopped) workerHandler.post { openAccelWindow() }
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    fun start() {
        stopped = false
        if (sigMotion != null) {
            sensorManager.requestTriggerSensor(triggerListener, sigMotion)
        } else {
            workerHandler.post { registerAccelContinuous() }
        }
    }

    fun stop() {
        stopped = true
        workerHandler.removeCallbacksAndMessages(null)
        sensorManager.unregisterListener(accelListener)
        sigMotion?.let { runCatching { sensorManager.cancelTriggerSensor(triggerListener, it) } }
        workerThread.quitSafely()
    }

    // ── Worker-thread helpers ──────────────────────────────────────────────────

    /** Opens the 3 s accelerometer window after a significant-motion trigger. */
    private fun openAccelWindow() {
        if (accelRegistered || stopped) return
        accelRegistered = true
        isShaking = false
        accelerometer?.let {
            sensorManager.registerListener(accelListener, it, SensorManager.SENSOR_DELAY_GAME, workerHandler)
        }
        // Auto-expire: 3 s window — sig motion fires at motion START, user may begin
        // deliberate shaking 1+ s later; 1.5 s was too tight in practice.
        workerHandler.postDelayed({ stopAccelAndRearm(rearmDelayMs = 200L) }, 3000L)
    }

    /** Fallback path: keep accelerometer on permanently (foreground-service-only path). */
    private fun registerAccelContinuous() {
        if (accelRegistered || stopped) return
        accelRegistered = true
        accelerometer?.let {
            sensorManager.registerListener(accelListener, it, SensorManager.SENSOR_DELAY_GAME, workerHandler)
        }
    }

    private fun stopAccelAndRearm(rearmDelayMs: Long) {
        workerHandler.removeCallbacksAndMessages(null)
        sensorManager.unregisterListener(accelListener)
        accelRegistered = false
        isShaking = false
        workerHandler.postDelayed({ rearmTrigger() }, rearmDelayMs)
    }

    private fun rearmTrigger() {
        if (!stopped) sigMotion?.let { sensorManager.requestTriggerSensor(triggerListener, it) }
    }

    companion object {
        /**
         * Single 80 ms haptic pulse confirming a shake gesture.
         * Called by shake callbacks AFTER tryShakeToggle succeeds so that exactly one
         * vibration fires per toggle — even when the composable and service helpers
         * both detect the same shake simultaneously.
         */
        fun hapticFeedback(context: Context) {
            val appCtx = context.applicationContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (appCtx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                    .defaultVibrator
                    .vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = appCtx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(80)
                }
            }
        }
    }
}
