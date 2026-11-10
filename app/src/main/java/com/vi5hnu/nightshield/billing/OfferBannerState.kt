package com.vi5hnu.nightshield.billing

import android.content.Context
import androidx.core.content.edit

/**
 * Remembers which offer the user dismissed the home-screen offer banner for.
 *
 * Keyed by the offer, not a global flag: "Not now" means "not this sale", so a new or rescheduled
 * discount in Play Console is announced again. The key combines Play's offer id (stable across
 * queries, unlike the offer token) with its end time, because the same offer id can be reused for
 * a later sale window.
 */
object OfferBannerState {

    private const val PREFS = "offer_banner_prefs"
    private const val KEY_DISMISSED = "dismissed_offer"

    fun isDismissed(context: Context, offer: ProOffer): Boolean =
        prefs(context).getString(KEY_DISMISSED, null) == keyOf(offer)

    fun dismiss(context: Context, offer: ProOffer) {
        prefs(context).edit { putString(KEY_DISMISSED, keyOf(offer)) }
    }

    internal fun keyOf(offer: ProOffer): String =
        "${offer.offerId ?: "price:${offer.priceMicros}"}@${offer.endsAtMillis ?: "open"}"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
