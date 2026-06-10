package com.vi5hnu.nightshield.widgets

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vi5hnu.nightshield.NightShieldManager
import com.vi5hnu.nightshield.ShakeHelper
import kotlin.math.sqrt

/**
 * Composable shake detector that is only active while the host Activity is
 * RESUMED.  On ON_PAUSE the sensor listener is unregistered so this does not
 * compete with NightShieldService / NightShieldAccessibilityService when the
 * app moves to the background.
 */
@Composable
fun ShakeDetector(onShake: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onShakeState = rememberUpdatedState(onShake)

    DisposableEffect(lifecycleOwner) {
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
                        // Route through the shared dedup gate so this and the service's
                        // ShakeHelper don't both fire (and vibrate) for the same physical shake.
                        NightShieldManager.tryShakeToggle {
                            ShakeHelper.hapticFeedback(context)
                            onShakeState.value()
                        }
                        isShaking = false
                    }
                    if (currentTime - lastShakeTimestamp > 300) {
                        isShaking = false
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> accelerometer?.let {
                    sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
                }
                Lifecycle.Event.ON_PAUSE -> sensorManager.unregisterListener(listener)
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensorManager.unregisterListener(listener)
        }
    }
}
