package com.vi5hnu.nightshield

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit

/**
 * The 24-hour Pro trial earned by watching a rewarded ad ([RewardedAdManager]).
 *
 * A trial unlocks Pro *features* ([ProGate.isPro]) but not ad removal ([ProGate.isPaid]).
 *
 * Anti-tamper rules — the trial lives entirely on the device, so it is timed in a way the user
 * cannot stretch:
 *  - Within the boot it was earned in, time is measured with [SystemClock.elapsedRealtime], which
 *    changing the date/time setting does not affect.
 *  - Across a reboot it falls back to the wall clock, but the highest wall time ever seen is
 *    recorded and a clock that moves backwards past [CLOCK_TOLERANCE_MS] ends the trial.
 *  - A trial cannot be started while one is running, and the stored state is excluded from
 *    backup/device transfer (see res/xml), so clearing data or restoring buys nothing — every trial
 *    costs a watched ad.
 *
 * Without a backend, a reward can't be checked against AdMob's server-side verification; the
 * reward is only ever granted from the SDK's own earned-reward callback.
 */
object ProTrial {

    const val DURATION_MS = 24 * 60 * 60 * 1000L
    /** Allowance for ordinary clock corrections (NTP sync, DST is not a wall-clock change). */
    internal const val CLOCK_TOLERANCE_MS = 5 * 60 * 1000L

    private const val PREFS = "trial_prefs"
    private const val KEY_START_WALL = "start_wall_ms"
    private const val KEY_START_ELAPSED = "start_elapsed_ms"
    private const val KEY_BOOT_COUNT = "boot_count"
    private const val KEY_MAX_SEEN_WALL = "max_seen_wall_ms"
    private const val RC_EXPIRY = 4001

    /** Snapshot of the persisted trial. */
    internal data class State(
        val startWall: Long,
        val startElapsed: Long,
        val bootCount: Int,
        val maxSeenWall: Long,
    )

    /**
     * Pure rule (unit-tested): milliseconds of trial left, or 0 if none / expired / tampered.
     *
     * @param bootCount current boot count, or -1 if the platform can't tell.
     */
    internal fun remainingMs(state: State?, nowWall: Long, nowElapsed: Long, bootCount: Int): Long {
        if (state == null || state.startWall <= 0) return 0
        // Clock moved backwards: before the trial began, or behind the latest time we've seen.
        if (nowWall + CLOCK_TOLERANCE_MS < state.startWall) return 0
        if (nowWall + CLOCK_TOLERANCE_MS < state.maxSeenWall) return 0

        val sameBoot = bootCount >= 0 && bootCount == state.bootCount && nowElapsed >= state.startElapsed
        val used = if (sameBoot) nowElapsed - state.startElapsed else nowWall - state.startWall
        return (DURATION_MS - used).coerceIn(0, DURATION_MS)
    }

    fun remainingMs(context: Context): Long {
        val prefs = context.trialPrefs()
        val state = prefs.readState() ?: return 0
        val now = System.currentTimeMillis()
        val remaining = remainingMs(state, now, SystemClock.elapsedRealtime(), bootCount(context))
        if (remaining > 0 && now > state.maxSeenWall) {
            prefs.edit { putLong(KEY_MAX_SEEN_WALL, now) }
        }
        return remaining
    }

    fun isActive(context: Context): Boolean = remainingMs(context) > 0

    /**
     * Pro features unlocked, readable in a cold process (widgets, receivers) where [ProGate] may not
     * reflect the trial yet: the paid cache or a running trial.
     */
    fun hasProFeatures(context: Context): Boolean =
        BillingManager.isProCached(context) || isActive(context)

    /**
     * Starts a trial. Called only from the rewarded ad's earned-reward callback.
     * Returns false (and changes nothing) if a trial is already running.
     */
    fun start(context: Context): Boolean {
        if (isActive(context)) return false
        val now = System.currentTimeMillis()
        context.trialPrefs().edit(commit = true) {
            putLong(KEY_START_WALL, now)
            putLong(KEY_START_ELAPSED, SystemClock.elapsedRealtime())
            putInt(KEY_BOOT_COUNT, bootCount(context))
            putLong(KEY_MAX_SEEN_WALL, now)
        }
        ProGate.setTrialActive(true)
        scheduleExpiry(context, now + DURATION_MS)
        refreshWidgets(context)
        return true
    }

    /**
     * Brings [ProGate] in line with the stored trial and, when a trial has just ended, applies the
     * free-tier limits exactly as a refund does. Call at process start, on app resume and from the
     * expiry alarm.
     */
    fun reconcile(context: Context) {
        val prefs = context.trialPrefs()
        val hadTrial = prefs.readState() != null
        val active = isActive(context)
        ProGate.setTrialActive(active)
        if (hadTrial && !active) {
            prefs.edit { clear() }
            // A paid user keeps everything; only a trial-only user falls back to free limits.
            if (!ProGate.isPro.value) OverlayHelpers.enforceFreeLimits(context)
            refreshWidgets(context)
        } else if (active) {
            // Re-arm after reboot (alarms don't survive one).
            scheduleExpiry(context, System.currentTimeMillis() + remainingMs(context))
        }
    }

    private fun scheduleExpiry(context: Context, atWallMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Inexact is fine: every read of the trial re-checks the time; this only makes the free
        // limits apply while the app is closed.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atWallMs, expiryIntent(context))
    }

    private fun expiryIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, RC_EXPIRY,
            Intent(context, TrialExpiryReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun refreshWidgets(context: Context) {
        NightShieldWidgetProvider.updateWidget(context)
        IntensityWidgetProvider.updateAll(context)
    }

    private fun bootCount(context: Context): Int =
        runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        }.getOrDefault(-1)

    private fun android.content.SharedPreferences.readState(): State? {
        val start = getLong(KEY_START_WALL, 0L)
        if (start <= 0) return null
        return State(
            startWall = start,
            startElapsed = getLong(KEY_START_ELAPSED, 0L),
            bootCount = getInt(KEY_BOOT_COUNT, -1),
            maxSeenWall = getLong(KEY_MAX_SEEN_WALL, start),
        )
    }

    private fun Context.trialPrefs() =
        applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Fires when a trial runs out so the free limits apply even if the app is never opened. */
class TrialExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ProTrial.reconcile(context)
    }
}
