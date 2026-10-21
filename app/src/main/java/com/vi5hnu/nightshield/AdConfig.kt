package com.vi5hnu.nightshield

import com.google.android.gms.ads.AdRequest

/**
 * Every AdMob unit ID and the shared ad request, in one place.
 *
 * Debug builds always use Google's official test units: clicking or repeatedly loading live ads
 * from a development build counts as invalid traffic and can get the AdMob account limited. Test
 * units serve real-looking "Test Ad" creatives with full fill, so every format can be exercised.
 * See https://developers.google.com/admob/android/test-ads.
 */
object AdConfig {

    val bannerUnitId: String =
        if (BuildConfig.DEBUG) "ca-app-pub-3940256099942544/9214589741" // adaptive banner test unit
        else "ca-app-pub-4715945578201106/5606751408"

    val interstitialUnitId: String =
        if (BuildConfig.DEBUG) "ca-app-pub-3940256099942544/1033173712"
        else "ca-app-pub-4715945578201106/8166997728"

    val appOpenUnitId: String =
        if (BuildConfig.DEBUG) "ca-app-pub-3940256099942544/9257395921"
        else "ca-app-pub-4715945578201106/6261357521"

    val rewardedUnitId: String =
        if (BuildConfig.DEBUG) "ca-app-pub-3940256099942544/5224354917"
        else "ca-app-pub-4715945578201106/8723374149"

    /**
     * Contextual keywords describing what the app is about, so AdMob can favour relevant
     * advertisers (sleep, eye care, wellness) — relevant ads are clicked more and pay more. They are
     * about the app's content only; nothing about the user is sent.
     */
    private val KEYWORDS = listOf(
        "sleep",
        "better sleep",
        "eye care",
        "eye strain",
        "blue light",
        "night mode",
        "screen time",
        "bedtime",
        "wellness",
        "health",
        "relaxation",
    )

    /** The request every ad format loads with. */
    fun request(): AdRequest =
        AdRequest.Builder()
            .apply { KEYWORDS.forEach { addKeyword(it) } }
            .build()
}
