package com.vi5hnu.nightshield

/**
 * Tracks whether one of our own full-screen ads (rewarded, interstitial) is on screen.
 *
 * A full-screen ad covers [MainActivity], which stops it. A rewarded ad can run past the app-open
 * ad's "away" threshold, so without this the return from it looked like a new visit and an app-open
 * ad was stacked straight on top — a poor experience that AdMob policy also forbids.
 * Set *before* `show()` so it is already true when the activity stops.
 */
object FullScreenAds {
    @Volatile
    var isShowing = false
        private set

    fun onShowRequested() { isShowing = true }
    fun onClosed() { isShowing = false }
}
