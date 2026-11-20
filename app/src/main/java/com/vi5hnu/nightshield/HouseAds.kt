package com.vi5hnu.nightshield

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Night Shield's own "go ad-free with Pro" promos, shown where an ad would have been when no ad
 * can be served.
 *
 * Ads can fail because the phone is offline, the network is poor, or the user has cut Night
 * Shield's internet (per-app data toggles, firewall apps) to skip ads. These cases are deliberately
 * NOT told apart: an innocent user with bad signal must never be treated as a cheat. Everyone gets
 * the same gentle treatment. Nothing is locked, nothing accuses ("ad blocker detected"), and the
 * promos are rarer than the ads they replace. They need no network and send no data, so they
 * also need no ad consent.
 *
 *  - Banner slot: a small Pro card (see `screens/HouseBanner.kt`), while the real banner retries.
 *  - Interstitial slot: at most one upgrade note per [UPSELL_MIN_INTERVAL_MS], requested by
 *    [InterstitialAdManager] at the moment a real interstitial would have shown. Real interstitials
 *    can come every 20 minutes, so blocking ads never makes the app noisier.
 *
 * Paid users never see either.
 */
object HouseAds {

    /** At most one upgrade note per day, however often the interstitial slot comes up empty. */
    internal const val UPSELL_MIN_INTERVAL_MS = 24 * 60 * 60 * 1000L

    /** Shares `ad_prefs` with the ad managers; the file is excluded from backup. */
    private const val PREFS = "ad_prefs"
    private const val KEY_LAST_UPSELL = "last_house_upsell_ms"

    private val _upsellDue = MutableStateFlow(false)
    /** True while the upgrade note should be on screen; the app UI shows it and calls [dismissUpsell]. */
    val upsellDue: StateFlow<Boolean> = _upsellDue.asStateFlow()

    /**
     * Pure rule (unit-tested): whether the daily cap allows another upgrade note.
     * A clock that moved backwards (lastMs in the future) can't keep the cap stuck forever: it
     * counts as a fresh start rather than blocking until the clock catches up.
     */
    internal fun upsellAllowed(nowMs: Long, lastMs: Long): Boolean =
        lastMs <= 0L || nowMs < lastMs || nowMs - lastMs >= UPSELL_MIN_INTERVAL_MS

    /**
     * Asks for the upgrade note in place of an interstitial that couldn't load.
     * Returns true when it will show, so the caller can treat its slot as used.
     */
    fun requestUpsell(context: Context): Boolean {
        if (ProGate.isPaid.value || _upsellDue.value) return false
        val prefs = context.adPrefs()
        val now = System.currentTimeMillis()
        if (!upsellAllowed(now, prefs.getLong(KEY_LAST_UPSELL, 0L))) return false
        // Counted when requested: the app is in the foreground (the user just switched the filter
        // off in it), so the note shows straight away. A rare miss only means one fewer note.
        prefs.edit { putLong(KEY_LAST_UPSELL, now) }
        _upsellDue.value = true
        return true
    }

    /** The note was answered ("See Pro" or "Not now") or is no longer relevant. */
    fun dismissUpsell() {
        _upsellDue.value = false
    }

    private fun Context.adPrefs() =
        applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
