package com.vi5hnu.nightshield.billing

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * User-facing wording for a [ProOffer], kept out of composables so it is unit-testable and
 * shared by the paywall and the Settings upgrade card.
 *
 * Deadlines are shown only when Play supplies one: inventing urgency would be a dark pattern, and
 * a near countdown for an offer that is really open-ended would be false.
 */
object OfferCopy {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /** Beyond this, a countdown reads as noise, so the end date is shown instead. */
    private const val COUNTDOWN_WINDOW = 7 * DAY

    /**
     * "Offer ends in 2d 5h" inside the last week, "Offer ends 25 Nov" before that, or null when
     * there is no end time or it has already passed (the caller then re-queries Play).
     */
    fun endsText(endsAtMillis: Long?, nowMillis: Long, locale: Locale = Locale.getDefault()): String? {
        val end = endsAtMillis ?: return null
        val left = end - nowMillis
        if (left <= 0) return null
        if (left >= COUNTDOWN_WINDOW) {
            // The year is added only when it differs, so "25 Nov" stays short but never ambiguous.
            val pattern = if (yearOf(end) == yearOf(nowMillis)) "d MMM" else "d MMM yyyy"
            return "Offer ends ${SimpleDateFormat(pattern, locale).format(Date(end))}"
        }
        val days = left / DAY
        val hours = (left % DAY) / HOUR
        // Round minutes up so the last minute reads "1m", not "0m".
        val minutes = ((left % HOUR) + MINUTE - 1) / MINUTE
        val span = when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
        return "Offer ends in $span"
    }

    private fun yearOf(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR)

    /**
     * Screen-reader text for the price block. Without it TalkBack reads the struck-through full
     * price as if it were the price.
     */
    fun priceDescription(offer: ProOffer): String = buildString {
        append("Price ${offer.formattedPrice}")
        offer.fullFormattedPrice?.let { append(", was $it") }
        offer.discountLabel?.let { append(", ${it.replace("%", " percent")}") }
    }
}
