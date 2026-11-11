package com.vi5hnu.nightshield

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Pause the filter for a minute" — the escape hatch for apps that refuse to work under a screen
 * overlay (Paytm, PhonePe, banking apps: they block touches as an anti-tapjacking measure).
 *
 * A pause does not stop the filter. [NightShieldService] stays in the foreground and takes its
 * overlay window fully off the screen (an invisible window still counts as an overlay to those
 * apps), then puts it back when the deadline passes. Free for everyone: it exists so people keep
 * the app installed, not to sell Pro.
 *
 * State is process-local on purpose. It only means anything while the service is alive, and a
 * restarted service should come back filtering rather than stuck paused.
 */
object FilterPause {

    /** How long one tap on Pause lasts. Short by default: a payment takes well under a minute. */
    enum class Duration(val seconds: Int, val label: String) {
        SECONDS_30(30, "30 sec"),
        MINUTE_1(60, "1 min"),
        MINUTES_2(120, "2 min"),
        MINUTES_5(300, "5 min");

        companion object {
            val DEFAULT = MINUTE_1
            fun fromSeconds(seconds: Int): Duration = entries.firstOrNull { it.seconds == seconds } ?: DEFAULT
        }
    }

    private const val PREFS = "filter_pause_prefs"
    private const val KEY_DURATION_SECONDS = "duration_seconds"

    /**
     * Deadline on the [SystemClock.elapsedRealtime] clock, or 0 when not paused. That clock keeps
     * counting through sleep and ignores the user changing the time, so a pause can neither be
     * stretched nor cut short by the wall clock.
     */
    private val _pausedUntilElapsedMs = MutableStateFlow(0L)
    val pausedUntilElapsedMs: StateFlow<Long> = _pausedUntilElapsedMs.asStateFlow()

    val isPaused: Boolean get() = _pausedUntilElapsedMs.value > 0L

    /**
     * The length last chosen in Settings during this process (null until it is changed). The
     * service watches it so the notification's "Pause 1 min" button never shows a stale length.
     */
    private val _durationChanges = MutableStateFlow<Duration?>(null)
    val durationChanges: StateFlow<Duration?> = _durationChanges.asStateFlow()

    /** Starts (or restarts) a pause of the user's chosen length. */
    fun pause(context: Context, nowElapsedMs: Long = SystemClock.elapsedRealtime()) {
        _pausedUntilElapsedMs.value = deadline(nowElapsedMs, duration(context))
    }

    /** Ends the pause now; a no-op when not paused. */
    fun resume() {
        _pausedUntilElapsedMs.value = 0L
    }

    fun remainingMs(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Long =
        remainingMs(_pausedUntilElapsedMs.value, nowElapsedMs)

    fun duration(context: Context): Duration =
        Duration.fromSeconds(prefs(context).getInt(KEY_DURATION_SECONDS, Duration.DEFAULT.seconds))

    fun setDuration(context: Context, duration: Duration) {
        prefs(context).edit { putInt(KEY_DURATION_SECONDS, duration.seconds) }
        _durationChanges.value = duration
    }

    internal fun deadline(nowElapsedMs: Long, duration: Duration): Long =
        nowElapsedMs + duration.seconds * 1000L

    internal fun remainingMs(untilElapsedMs: Long, nowElapsedMs: Long): Long =
        if (untilElapsedMs <= 0L) 0L else (untilElapsedMs - nowElapsedMs).coerceAtLeast(0L)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
