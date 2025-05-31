package com.vi5hnu.nightshield.widgets

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlin.math.sqrt

@Composable
fun ShakeDetector(minShakeDuration:Int=1000,shakeThreshold: Float=10f,onShake: () -> Unit) {
    val context = LocalContext.current
    val onShakeState = rememberUpdatedState(onShake) // Capture latest lambda

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val sensorEventListener = object : SensorEventListener {
            private var lastShakeTimestamp = 0L
            private var shakeStartTimestamp = 0L
            private var isShaking = false

            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = event.values
                val acceleration = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH

                val currentTime = System.currentTimeMillis()

                if (acceleration > shakeThreshold) {
                    if (!isShaking) {
                        // Start shake detection
                        shakeStartTimestamp = currentTime
                        isShaking = true
                    }
                    lastShakeTimestamp = currentTime
                }

                if (isShaking) {
                    // If shaking lasts for at least 1 second, trigger onShake()
                    if (currentTime - shakeStartTimestamp >= minShakeDuration) {
                        onShakeState.value()
                        isShaking = false // Reset shake detection
                    }
                    // If user stops shaking before 1 second, reset
                    if (currentTime - lastShakeTimestamp > 300) { // No movement for 300ms
                        isShaking = false
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        accelerometer?.let {
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            Log.i("ShakeDetector","Shake detector disposed");
            sensorManager.unregisterListener(sensorEventListener)
        }
    }
}
