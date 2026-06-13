package com.vi5hnu.nightshield

import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * Self-contained sunrise/sunset calculator (the standard "sunrise equation", NOAA approximation).
 * No network and no third-party dependency. Returns UTC instants (epoch millis) which can be fed
 * straight to AlarmManager (RTC) and converted to local time for display.
 */
object SunTimes {

    private const val RAD = PI / 180.0

    /**
     * Sunrise & sunset epoch-millis for [date] at [lat]/[lng], or null on polar day/night
     * (when the sun doesn't cross the horizon that day at that latitude).
     */
    fun compute(lat: Double, lng: Double, date: LocalDate): Pair<Long, Long>? {
        // Julian day number for the date (noon UTC reference).
        val jdate = date.toEpochDay() + 2440587.5 + 0.5
        val n = Math.round(jdate - 2451545.0 + 0.0008).toDouble()

        val jStar = n - lng / 360.0
        val m = (357.5291 + 0.98560028 * jStar) % 360.0
        val mRad = m * RAD
        val c = 1.9148 * sin(mRad) + 0.0200 * sin(2 * mRad) + 0.0003 * sin(3 * mRad)
        val lambda = (m + c + 180.0 + 102.9372) % 360.0
        val lambdaRad = lambda * RAD
        val jTransit = 2451545.0 + jStar + 0.0053 * sin(mRad) - 0.0069 * sin(2 * lambdaRad)

        val sinDecl = sin(lambdaRad) * sin(23.44 * RAD)
        val cosDecl = cos(asin(sinDecl))
        val cosOmega = (sin(-0.833 * RAD) - sin(lat * RAD) * sinDecl) / (cos(lat * RAD) * cosDecl)
        if (cosOmega > 1.0 || cosOmega < -1.0) return null   // polar night / midnight sun

        val omega = acos(cosOmega) / RAD                     // hour angle in degrees
        val jRise = jTransit - omega / 360.0
        val jSet = jTransit + omega / 360.0
        return julianToEpochMs(jRise) to julianToEpochMs(jSet)
    }

    private fun julianToEpochMs(jd: Double): Long = ((jd - 2440587.5) * 86_400_000.0).toLong()
}
