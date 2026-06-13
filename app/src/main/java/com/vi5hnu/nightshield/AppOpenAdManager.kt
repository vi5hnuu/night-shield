package com.vi5hnu.nightshield

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

private const val APP_OPEN_AD_UNIT_ID = "ca-app-pub-4715945578201106/6261357521"
private const val SHOW_COOLDOWN_MS = 60 * 60 * 1000L  // 60 minutes between shows

object AppOpenAdManager {

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private var loadTime: Long = 0
    private var lastShownTime: Long = 0

    /** Load a fresh App Open ad. Safe to call multiple times — ignores if already loading/loaded. */
    fun loadAd(context: Context) {
        if (isLoadingAd || isAdAvailable()) return
        isLoadingAd = true
        AppOpenAd.load(
            context,
            APP_OPEN_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = System.currentTimeMillis()
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingAd = false
                }
            }
        )
    }

    /** Show the ad if available, then reload for next time. Pro users never see this ad. */
    fun showAdIfAvailable(activity: Activity, onComplete: () -> Unit = {}) {
        if (ProGate.isPro.value) { onComplete(); return }
        if (isShowingAd) { onComplete(); return }
        if (!isAdAvailable()) { onComplete(); loadAd(activity); return }
        if (System.currentTimeMillis() - lastShownTime < SHOW_COOLDOWN_MS) { onComplete(); return }

        // Set flag before show() so a second rapid call to showAdIfAvailable is blocked immediately
        isShowingAd = true
        lastShownTime = System.currentTimeMillis()
        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowingAd = false
                onComplete()
                loadAd(activity)
            }
            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                appOpenAd = null
                isShowingAd = false
                onComplete()
                loadAd(activity)
            }
        }
        // Guard against a synchronous throw (e.g., activity in destroying state) which would
        // leave isShowingAd stuck at true, blocking every future ad show for the session.
        try {
            appOpenAd?.show(activity)
        } catch (_: Exception) {
            appOpenAd = null
            isShowingAd = false
            onComplete()
            loadAd(activity)
        }
    }

    // Ads expire after 4 hours
    private fun isAdAvailable() =
        appOpenAd != null && (System.currentTimeMillis() - loadTime) < 4 * 60 * 60 * 1000
}
