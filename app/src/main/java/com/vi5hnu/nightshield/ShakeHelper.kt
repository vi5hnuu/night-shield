package com.vi5hnu.nightshield

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Continuous-accelerometer shake detector using the well-proven algorithm from Square's
 * Seismic library — the de-facto standard used by most shake-to-X apps.
 *
 * How it distinguishes a real shake from picking up / moving the phone:
 *   - Each accelerometer sample is "accelerating" if its total magnitude exceeds a threshold
 *     (~1.3 g for Normal). At rest the magnitude is ~1 g, so it is not accelerating.
 *   - A shake is reported only when, over a sliding ~0.5 s window spanning at least 0.25 s,
 *     at least 3/4 of the samples are accelerating.
 *   A single pickup is one short acceleration spike — nowhere near 3/4 of the window. Moving
 *   the phone slowly never crosses the threshold at all. Only sustained, vigorous, oscillating
 *   motion (an actual shake) fills the window.
 *
 * Runs continuously inside a foreground service (which keeps the process alive while the app is
 * closed), with all sensor work on a dedicated HandlerThread so the main thread is never blocked.
 */
class ShakeHelper(
    context: Context,
    private val thresholdProvider: () -> Float = { NightShieldManager.shakeIntensity.value.threshold },
    private val onShake: () -> Unit,
) {
    private val appCtx        = context.applicationContext
    private val sensorManager = appCtx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val workerThread  = HandlerThread("night-shield-shake").also { it.start() }
    private val workerHandler = Handler(workerThread.looper)
    private val mainHandler   = Handler(Looper.getMainLooper())

    // Sliding window of recent samples — only ever touched on the worker thread.
    private val timestamps   = ArrayDeque<Long>()     // event timestamps (ns)
    private val accelerating = ArrayDeque<Boolean>()
    private var acceleratingCount = 0
    private var lastFireMs = 0L
    // Written on the calling thread in start(), read on the worker thread in the listener.
    @Volatile private var startMs = 0L

    @Volatile private var registered = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val magnitudeSq = x * x + y * y + z * z
            val threshold   = thresholdProvider()
            val isAccel     = magnitudeSq > threshold * threshold
            val ts          = event.timestamp

            timestamps.addLast(ts)
            accelerating.addLast(isAccel)
            if (isAccel) acceleratingCount++

            // Drop samples older than the window.
            val cutoff = ts - MAX_WINDOW_NS
            while (timestamps.isNotEmpty() && timestamps.first() < cutoff) {
                timestamps.removeFirst()
                if (accelerating.removeFirst()) acceleratingCount--
            }

            // Shake = window spans ≥0.25 s AND ≥3/4 of its samples are accelerating.
            val span = ts - timestamps.first()
            if (span >= MIN_WINDOW_NS && acceleratingCount * 4 >= accelerating.size * 3) {
                val now = System.currentTimeMillis()
                // Startup grace: a detector started by a shake-driven service handoff must NOT
                // act on the tail of the very gesture that spawned it (which would immediately
                // toggle back). During normal idle monitoring this is long expired.
                if (now - startMs >= STARTUP_GRACE_MS && now - lastFireMs >= FIRE_COOLDOWN_MS) {
                    lastFireMs = now
                    clearWindow()                 // require a fresh shake for the next toggle
                    mainHandler.post(onShake)
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun start() {
        val accel = accelerometer ?: return
        if (registered) return
        registered = true
        startMs = System.currentTimeMillis()
        // SENSOR_DELAY_GAME (~50 Hz) gives enough samples for the windowed fraction to be reliable.
        sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME, workerHandler)
    }

    fun stop() {
        if (registered) {
            sensorManager.unregisterListener(listener)
            registered = false
        }
        workerHandler.post { clearWindow() }
        workerThread.quitSafely()
    }

    private fun clearWindow() {
        timestamps.clear()
        accelerating.clear()
        acceleratingCount = 0
    }

    companion object {
        private const val MAX_WINDOW_NS    = 500_000_000L  // 0.5 s sliding window
        private const val MIN_WINDOW_NS    = 250_000_000L  // need ≥0.25 s of data before deciding
        private const val FIRE_COOLDOWN_MS = 1000L         // ignore re-fires within 1 s of a shake
        private const val STARTUP_GRACE_MS = 1500L         // ignore the gesture that spawned this detector

        /**
         * Single 80 ms haptic pulse confirming a shake gesture. Called by shake callbacks AFTER
         * tryShakeToggle succeeds so exactly one vibration fires per toggle.
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
