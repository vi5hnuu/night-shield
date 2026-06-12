package com.vi5hnu.nightshield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

/**
 * Shake detector that is reliable both in-app and in the background, with a sensible battery
 * profile, by adapting its strategy to the screen state.
 *
 * Why screen-aware
 * ----------------
 * A *deliberate shake* can only be detected by reading the accelerometer continuously and watching
 * for the threshold-crossing pattern. TYPE_SIGNIFICANT_MOTION is NOT a shake detector — it is tuned
 * to detect motion that changes the user's location (walking, getting in a car) and is deliberately
 * insensitive to brief in-hand movement, so it frequently does not fire on a shake.
 *
 * But a continuous accelerometer only works while the CPU is awake. A foreground service keeps the
 * *process* alive, not the *CPU* — with the screen off the device suspends and a normal accelerometer
 * is starved of events. Running it anyway (with a wake lock) drains 5–10%/hour.
 *
 * So:
 *   - Screen ON  → continuous accelerometer. Reliable; the screen already dominates power draw.
 *   - Screen OFF → arm TYPE_SIGNIFICANT_MOTION (a low-power hardware wake-up sensor). When the user
 *                  picks up / shakes the phone it fires, we briefly hold a wake lock and open an
 *                  accelerometer window to confirm the shake, then re-arm. Near-zero idle cost.
 *
 * Fallback (device without significant-motion hardware): continuous accelerometer in both states.
 *
 * All sensor work runs on a dedicated HandlerThread; mode transitions are posted there too, so the
 * internal state is only ever touched on one thread.
 */
class ShakeHelper(
    context: Context,
    private val thresholdProvider: () -> Float = { NightShieldManager.shakeIntensity.value.threshold },
    private val durationProvider: () -> Int    = { NightShieldManager.shakeIntensity.value.durationMs },
    private val onShake: () -> Unit,
) {
    private val appCtx        = context.applicationContext
    private val sensorManager = appCtx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val powerManager  = appCtx.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val sigMotion     = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

    // Held only for the brief screen-off confirmation window so the CPU stays awake long enough to
    // read the accelerometer after a significant-motion trigger. Auto-times out as a safety net.
    private val wakeLock: PowerManager.WakeLock =
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NightShield:ShakeDetect")
            .apply { setReferenceCounted(false) }

    private val workerThread  = HandlerThread("night-shield-shake").also { it.start() }
    private val workerHandler = Handler(workerThread.looper)
    private val mainHandler   = Handler(Looper.getMainLooper())

    /** CONTINUOUS = accel always on (screen on / no sig-motion). ARMED = waiting on sig-motion
     *  (screen off, idle). WINDOW = brief accel window after a sig-motion trigger. */
    private enum class Mode { CONTINUOUS, ARMED, WINDOW }

    // ── Worker-thread-only state (single-threaded, no synchronisation needed) ──
    private var mode            = Mode.ARMED
    private var accelRegistered = false
    private var sigMotionArmed  = false
    private var isShaking       = false
    private var shakeStartMs    = 0L
    private var lastShakeMs     = 0L
    private var lastFireMs      = 0L

    @Volatile private var stopped = false

    private val windowTimeout = Runnable { if (mode == Mode.WINDOW) enterArmed() }

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
                        // In a screen-off window: close it and re-arm. In continuous mode keep
                        // listening — the 2 s lastFireMs cooldown prevents an immediate re-fire.
                        if (mode == Mode.WINDOW) enterArmed()
                    }
                    // Tolerate brief dips below threshold between shake oscillations.
                    now - lastShakeMs > 800L -> isShaking = false
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // ── Significant-motion trigger (one-shot; delivered on main thread) ────────
    private val triggerListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent) {
            // Hardware disarmed itself after firing; reflect that and open the confirm window.
            if (!stopped) workerHandler.post {
                sigMotionArmed = false
                if (mode == Mode.ARMED) enterWindow()
            }
        }
    }

    // ── Screen on/off switches the detection strategy ─────────────────────────
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON  -> workerHandler.post { if (!stopped) enterContinuous() }
                Intent.ACTION_SCREEN_OFF -> workerHandler.post { if (!stopped) enterArmed() }
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────
    fun start() {
        stopped = false
        ContextCompat.registerReceiver(
            appCtx, screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        workerHandler.post {
            // No significant-motion hardware → continuous accel is the only option in both states.
            if (powerManager.isInteractive || sigMotion == null) enterContinuous() else enterArmed()
        }
    }

    fun stop() {
        stopped = true
        runCatching { appCtx.unregisterReceiver(screenReceiver) }
        workerHandler.post {
            workerHandler.removeCallbacks(windowTimeout)
            unregisterAccel()
            cancelSigMotion()
            if (wakeLock.isHeld) runCatching { wakeLock.release() }
            workerThread.quitSafely()
        }
    }

    // ── Mode transitions (all run on workerThread) ────────────────────────────
    private fun enterContinuous() {
        workerHandler.removeCallbacks(windowTimeout)
        cancelSigMotion()
        if (wakeLock.isHeld) runCatching { wakeLock.release() }
        mode = Mode.CONTINUOUS
        registerAccel()
    }

    private fun enterArmed() {
        workerHandler.removeCallbacks(windowTimeout)
        unregisterAccel()
        if (wakeLock.isHeld) runCatching { wakeLock.release() }
        if (sigMotion == null) {
            // Best effort without a wake-up sensor: stay continuous.
            mode = Mode.CONTINUOUS
            registerAccel()
        } else {
            mode = Mode.ARMED
            armSigMotion()
        }
    }

    private fun enterWindow() {
        mode = Mode.WINDOW
        isShaking = false
        if (!wakeLock.isHeld) wakeLock.acquire(WINDOW_MS + 500L)
        registerAccel()
        workerHandler.postDelayed(windowTimeout, WINDOW_MS)
    }

    // ── Sensor (un)registration helpers (idempotent) ──────────────────────────
    private fun registerAccel() {
        if (accelRegistered || stopped) return
        accelerometer?.let {
            // maxReportLatencyUs = 0: immediate delivery, no batching (matters in the short window).
            sensorManager.registerListener(accelListener, it, SensorManager.SENSOR_DELAY_UI, 0, workerHandler)
            accelRegistered = true
        }
    }

    private fun unregisterAccel() {
        if (!accelRegistered) return
        sensorManager.unregisterListener(accelListener)
        accelRegistered = false
        isShaking = false
    }

    private fun armSigMotion() {
        if (sigMotionArmed || stopped) return
        sigMotion?.let { if (sensorManager.requestTriggerSensor(triggerListener, it)) sigMotionArmed = true }
    }

    private fun cancelSigMotion() {
        if (!sigMotionArmed) return
        sigMotion?.let { runCatching { sensorManager.cancelTriggerSensor(triggerListener, it) } }
        sigMotionArmed = false
    }

    companion object {
        private const val WINDOW_MS = 3000L  // screen-off accel confirm window after a trigger

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
