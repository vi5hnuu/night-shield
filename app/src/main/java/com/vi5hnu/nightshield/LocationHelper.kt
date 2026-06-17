package com.vi5hnu.nightshield

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.Locale

/**
 * Reads the device location (caller must already hold ACCESS_COARSE_LOCATION), reverse-geocodes a
 * city name, and caches both via [OverlayHelpers.saveAutoLocation]. Used only for the auto-schedule.
 */
object LocationHelper {

    /** [onResult] is invoked on the main thread with the resolved city, or null on failure. */
    @SuppressLint("MissingPermission")
    fun fetchAndCache(context: Context, onResult: (city: String?) -> Unit) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Prefer the freshest cached fix across providers; fall back to a single live update.
        var best: Location? = null
        for (p in listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )) {
            if (runCatching { lm.isProviderEnabled(p) }.getOrDefault(false)) {
                val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull()
                if (loc != null && (best == null || loc.time > best!!.time)) best = loc
            }
        }

        if (best != null) {
            cache(context, best!!.latitude, best!!.longitude, onResult)
        } else {
            requestSingle(context, lm, onResult)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestSingle(context: Context, lm: LocationManager, onResult: (String?) -> Unit) {
        val provider = when {
            runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
                LocationManager.NETWORK_PROVIDER
            runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ->
                LocationManager.GPS_PROVIDER
            else -> { onResult(null); return }
        }
        val main = Handler(Looper.getMainLooper())
        val listener = object : LocationListener {
            var done = false
            override fun onLocationChanged(location: Location) {
                if (done) return
                done = true
                runCatching { lm.removeUpdates(this) }
                cache(context, location.latitude, location.longitude, onResult)
            }
            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        runCatching { lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper()) }
            .onFailure { onResult(null); return }
        // Give up after 15 s so the UI doesn't hang.
        main.postDelayed({
            if (!listener.done) {
                listener.done = true
                runCatching { lm.removeUpdates(listener) }
                onResult(null)
            }
        }, 15_000)
    }

    /** Reverse-geocode on a background thread (it may hit the network), persist, then call back. */
    private fun cache(context: Context, lat: Double, lon: Double, onResult: (String?) -> Unit) {
        Thread {
            val city = runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
            }.getOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea } ?: "Current location"

            OverlayHelpers.saveAutoLocation(context, lat, lon, city)
            Handler(Looper.getMainLooper()).post {
                NightShieldManager.setAutoCity(city)
                onResult(city)
            }
        }.start()
    }
}
