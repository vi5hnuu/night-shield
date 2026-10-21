package com.vi5hnu.nightshield

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Watch an ad, try Pro for 24 hours" — the rewarded ad behind [ProTrial].
 *
 * Abuse handling:
 *  - The trial is granted only from the SDK's `onUserEarnedReward`, which fires when the ad has
 *    been watched to its reward point — closing early, a failed load or a failed show grant nothing.
 *  - The ad is shown only on a validated internet connection. An ad loaded earlier can otherwise be
 *    played after switching to airplane mode, where the impression may never be reported — the
 *    user would get the reward and the app nothing.
 *  - No new ad while a trial runs, and never for paid users.
 *  - The reward is persisted synchronously ([ProTrial.start] commits), so killing the app straight
 *    after the reward point cannot lose or duplicate it.
 */
object RewardedAdManager {

    enum class Availability { LOADING, READY, UNAVAILABLE }

    /** Outcome of a tap on "watch an ad", for the UI to explain. */
    enum class ShowResult { SHOWN, OFFLINE, NOT_READY, TRIAL_ALREADY_ACTIVE, PAID }

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    private val _availability = MutableStateFlow(Availability.UNAVAILABLE)
    val availability: StateFlow<Availability> = _availability.asStateFlow()

    fun loadAd(context: Context) {
        if (ProGate.isPaid.value || isLoading || rewardedAd != null) return
        if (!isOnline(context)) {
            _availability.value = Availability.UNAVAILABLE
            return
        }
        isLoading = true
        _availability.value = Availability.LOADING
        RewardedAd.load(
            context.applicationContext,
            AdConfig.rewardedUnitId,
            AdConfig.request(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isLoading = false
                    _availability.value = Availability.READY
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    isLoading = false
                    _availability.value = Availability.UNAVAILABLE
                }
            },
        )
    }

    /**
     * Shows the ad if every precondition holds. [onTrialStarted] runs only when the reward was
     * earned and a trial actually began.
     */
    fun show(activity: Activity, onTrialStarted: () -> Unit): ShowResult {
        if (ProGate.isPaid.value) return ShowResult.PAID
        if (ProTrial.isActive(activity)) return ShowResult.TRIAL_ALREADY_ACTIVE
        if (!isOnline(activity)) return ShowResult.OFFLINE
        val ad = rewardedAd ?: run { loadAd(activity); return ShowResult.NOT_READY }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = reload(activity)
            override fun onAdFailedToShowFullScreenContent(e: AdError) = reload(activity)
        }
        // A rewarded ad is single-use: drop it now so a second tap can't replay the same one.
        rewardedAd = null
        _availability.value = Availability.UNAVAILABLE
        ad.show(activity) {
            if (ProTrial.start(activity)) onTrialStarted()
        }
        return ShowResult.SHOWN
    }

    private fun reload(context: Context) {
        rewardedAd = null
        // Preload the next one only if it could be used; during a trial it would just expire.
        if (!ProTrial.isActive(context)) loadAd(context)
    }

    /** True only for a network the system has verified actually reaches the internet. */
    private fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
