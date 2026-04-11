package com.vi5hnu.nightshield

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.sqrt

/**
 * Plain (non-Compose) shake detector for use in background services.
 * Call [start] when the service starts and [stop] when it stops.
 *
 * [thresholdProvider] and [durationProvider] are evaluated on every sensor
 * event so changes to [NightShieldManager.shakeIntensity] take effect
 * immediately without restarting the service.
 */
class ShakeHelper(
    private val context: Context,
    private val thresholdProvider: () -> Float = { NightShieldManager.shakeIntensity.value.threshold },
    private val durationProvider: () -> Int = { NightShieldManager.shakeIntensity.value.durationMs },
    private val onShake: () -> Unit
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var lastShakeTimestamp = 0L
    private var shakeStartTimestamp = 0L
    private var isShaking = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val (x, y, z) = event.values
            val acceleration = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
            val currentTime = System.currentTimeMillis()
            val threshold = thresholdProvider()
            val duration = durationProvider()

            if (acceleration > threshold) {
                if (!isShaking) {
                    shakeStartTimestamp = currentTime
                    isShaking = true
                }
                lastShakeTimestamp = currentTime
            }

            if (isShaking) {
                if (currentTime - shakeStartTimestamp >= duration) {
                    vibrate()
                    onShake()
                    isShaking = false
                }
                if (currentTime - lastShakeTimestamp > 300) {
                    isShaking = false
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(listener)
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(80)
            }
        }
    }
}
