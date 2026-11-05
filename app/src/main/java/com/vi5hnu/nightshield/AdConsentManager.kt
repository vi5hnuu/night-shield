package com.vi5hnu.nightshield

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ad consent through Google's User Messaging Platform (UMP), and the single gate every ad load
 * goes through.
 *
 * Google requires a certified consent message before ads are served to users in the EEA, the UK
 * and Switzerland. UMP decides from the device's region whether a message is needed, shows the
 * form configured in the AdMob console (Privacy & messaging), and stores the user's choice in the
 * standard IAB TCF keys, which the Mobile Ads SDK reads on every request. Nothing is sent to us.
 *
 * Flow (Google's recommended order):
 *  1. [gatherConsent] on every launch refreshes the consent status and shows the form only if one
 *     is required and not yet answered.
 *  2. Ads may load as soon as [ConsentInformation.canRequestAds] is true — that can already be the
 *     case from a previous session, so it is checked before *and* after the refresh to avoid
 *     delaying ads by a network round trip.
 *  3. [adsReady] flips to true once the Mobile Ads SDK has been started; the ad managers and the
 *     banner check it, so no ad request is ever made before consent.
 *  4. Where the law gives users the right to change their mind, [privacyOptionsRequired] is true
 *     and Settings shows an "Ad privacy choices" row that calls [showPrivacyOptionsForm].
 *
 * Paid users are not exempt from the refresh: the ad managers already skip them, and a refunded
 * user must have a valid consent state before ads return.
 */
object AdConsentManager {

    private val mobileAdsStarted = AtomicBoolean(false)

    private val _adsReady = MutableStateFlow(false)
    /** True once ads may be requested: consent allows it and the Mobile Ads SDK is started. */
    val adsReady: StateFlow<Boolean> = _adsReady.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    /** True where users must be able to revisit their choice (shows the Settings entry). */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    private fun consentInfo(context: Context): ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context.applicationContext)

    /**
     * Refreshes consent, shows the consent form if required, then starts ads if allowed.
     * [onAdsReady] runs once, on the main thread, when ads may be loaded.
     */
    fun gatherConsent(activity: Activity, onAdsReady: () -> Unit) {
        val info = consentInfo(activity)

        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    // A form error (offline, no form configured) is not fatal: canRequestAds
                    // still reflects whatever status UMP holds, so it decides below.
                    onConsentResolved(activity, onAdsReady)
                }
            },
            {
                // Status refresh failed (typically offline): fall back to the stored status.
                onConsentResolved(activity, onAdsReady)
            },
        )

        // A choice made in an earlier session already permits ads; don't wait for the refresh.
        if (info.canRequestAds()) startAds(activity, onAdsReady)
    }

    /** Opens UMP's privacy options form so the user can change their ad consent. */
    fun showPrivacyOptionsForm(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            // The new choice is stored by UMP and read by the SDK on the next ad request.
            refreshState(activity)
        }
    }

    private fun onConsentResolved(activity: Activity, onAdsReady: () -> Unit) {
        refreshState(activity)
        if (consentInfo(activity).canRequestAds()) startAds(activity, onAdsReady)
    }

    private fun refreshState(context: Context) {
        _privacyOptionsRequired.value = consentInfo(context).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    /** Starts the Mobile Ads SDK exactly once per process, then reports ads as ready. */
    private fun startAds(context: Context, onAdsReady: () -> Unit) {
        if (!mobileAdsStarted.compareAndSet(false, true)) return
        MobileAds.initialize(context.applicationContext)
        _adsReady.value = true
        onAdsReady()
    }
}
