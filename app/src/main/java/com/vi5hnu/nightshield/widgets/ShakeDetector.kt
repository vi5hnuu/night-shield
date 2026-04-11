package com.vi5hnu.nightshield.widgets

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.vi5hnu.nightshield.NightShieldManager
import kotlin.math.sqrt

@Composable
fun ShakeDetector(onShake: () -> Unit) {
    val context = LocalContext.current
    val onShakeState = rememberUpdatedState(onShake)

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            private var lastShakeTimestamp = 0L
            private var shakeStartTimestamp = 0L
            private var isShaking = false

            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = event.values
                val acceleration = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
                val currentTime = System.currentTimeMillis()
                // Read dynamically so intensity changes apply without recomposition
                val intensity = NightShieldManager.shakeIntensity.value

                if (acceleration > intensity.threshold) {
                    if (!isShaking) {
                        shakeStartTimestamp = currentTime
                        isShaking = true
                    }
                    lastShakeTimestamp = currentTime
                }

                if (isShaking) {
                    if (currentTime - shakeStartTimestamp >= intensity.durationMs) {
                        vibrate(context)
                        onShakeState.value()
                        isShaking = false
                    }
                    if (currentTime - lastShakeTimestamp > 300) {
                        isShaking = false
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        accelerometer?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose { sensorManager.unregisterListener(listener) }
    }
}

private fun vibrate(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator.vibrate(
            VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
        )
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
