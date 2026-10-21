package com.vi5hnu.nightshield

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Full-screen ad at a natural break: the user switching the filter off from inside the app.
 *
 * Every filter session that ends counts — from the app, widget, quick-settings tile, notification,
 * shake, sleep timer or a schedule — via [recordStop] in [NightShieldService.onDestroy]. The count
 * used to be in memory and fed only by the in-app button, so it reset whenever the process died and
 * most people (who switch off from the notification or widget) were effectively never shown one.
 *
 * It is still only *shown* inside the app after an in-app stop: showing a full-screen ad over the
 * home screen, or as the app opens, is against AdMob policy and a poor experience.
 * Paid users never see it; a rewarded-trial user still does (the trial unlocks features, not ad
 * removal).
 */
object InterstitialAdManager {

    /** Finished filter sessions between two interstitials. */
    private const val STOPS_PER_AD = 3
    /** Minimum gap between two interstitials, however often the filter is toggled. */
    private const val MIN_INTERVAL_MS = 20 * 60 * 1000L
    /**
     * Lets the service's onDestroy (which records the stop that triggered this) run first, and
     * shows the ad only once the filter has visibly switched off.
     */
    private const val SHOW_DELAY_MS = 600L

    private const val PREFS = "ad_prefs"
    private const val KEY_STOPS = "stops_since_interstitial"
    private const val KEY_LAST_SHOWN = "last_interstitial_ms"

    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false

    fun loadAd(context: Context) {
        if (ProGate.isPaid.value || isLoading || interstitialAd != null) return
        isLoading = true
        InterstitialAd.load(
            context,
            AdConfig.interstitialUnitId,
            AdConfig.request(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoading = false
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isLoading = false
                }
            }
        )
    }

    /** Counts one finished filter session, whatever stopped it. Safe from any process state. */
    fun recordStop(context: Context) {
        val prefs = context.adPrefs()
        prefs.edit { putInt(KEY_STOPS, prefs.getInt(KEY_STOPS, 0) + 1) }
    }

    /** Called right after the user switches the filter off from inside the app. */
    fun onFilterStopped(activity: Activity) {
        if (ProGate.isPaid.value) return
        Handler(Looper.getMainLooper()).postDelayed({ maybeShow(activity) }, SHOW_DELAY_MS)
    }

    private fun maybeShow(activity: Activity) {
        if (activity.isDestroyed || activity.isFinishing || ProGate.isPaid.value) return
        val prefs = activity.adPrefs()
        val now = System.currentTimeMillis()
        if (prefs.getInt(KEY_STOPS, 0) < STOPS_PER_AD) return
        if (now - prefs.getLong(KEY_LAST_SHOWN, 0L) < MIN_INTERVAL_MS) return
        val ad = interstitialAd ?: run { loadAd(activity); return }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                // Reset only once it is actually on screen, so a failed show keeps the debt.
                prefs.edit {
                    putInt(KEY_STOPS, 0)
                    putLong(KEY_LAST_SHOWN, System.currentTimeMillis())
                }
            }
            override fun onAdDismissedFullScreenContent() {
                FullScreenAds.onClosed()
                interstitialAd = null
                loadAd(activity)
            }
            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                FullScreenAds.onClosed()
                interstitialAd = null
                loadAd(activity)
            }
        }
        interstitialAd = null
        FullScreenAds.onShowRequested()
        ad.show(activity)
    }

    private fun Context.adPrefs() =
        applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
