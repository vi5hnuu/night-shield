package com.vi5hnu.nightshield

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-4715945578201106/8166997728"
private const val SHOW_EVERY_N_STOPS = 5

object InterstitialAdManager {

    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private var stopCount = 0

    fun loadAd(context: Context) {
        if (isLoading || interstitialAd != null) return
        isLoading = true
        InterstitialAd.load(
            context,
            INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
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

    /**
     * Called each time the user stops the filter. Shows an interstitial every
     * [SHOW_EVERY_N_STOPS] stops for free users; Pro users are never shown the ad.
     */
    fun onFilterStopped(activity: Activity) {
        if (ProGate.isPro.value) return
        stopCount++
        if (stopCount % SHOW_EVERY_N_STOPS != 0) return
        maybeShow(activity)
    }

    private fun maybeShow(activity: Activity) {
        if (activity.isDestroyed || activity.isFinishing) return
        val ad = interstitialAd ?: run { loadAd(activity); return }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                loadAd(activity)
            }
            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                interstitialAd = null
                loadAd(activity)
            }
        }
        ad.show(activity)
    }
}
