package com.vi5hnu.nightshield.billing

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * What the paywall shows, and what the purchase sheet is launched with.
 *
 * A plain model so the UI never touches Play's `ProductDetails`, and so the price on screen and
 * the [offerToken] handed to `launchBillingFlow` always come from the same selection.
 *
 * @property offerToken Must be passed to `ProductDetailsParams.setOfferToken`; without it Play
 *   charges the backwards-compatible base price even while a discount is live. Null only for a
 *   legacy base option Play returned without a token, which is exactly what Play charges by default.
 * @property formattedPrice Play's localised price for this offer (what the user will pay).
 * @property fullFormattedPrice The undiscounted price, shown struck through. Null when the offer is
 *   not a real discount.
 * @property discountLabel "Save 40%" / "Save ₹20", or null when there is no discount.
 * @property endsAtMillis When Play says the offer ends (wall clock), or null for open-ended offers.
 */
data class ProOffer(
    val offerToken: String?,
    val formattedPrice: String,
    val priceMicros: Long,
    val currencyCode: String,
    val fullFormattedPrice: String?,
    val discountLabel: String?,
    val endsAtMillis: Long?,
) {
    val isDiscounted: Boolean get() = fullFormattedPrice != null
}

/**
 * Play-independent view of one `OneTimePurchaseOfferDetails`, so [OfferSelector] is unit-testable.
 * `BillingManager` maps Play's objects into this in a single adapter.
 *
 * @property offerId Null for a purchase option's base offer; set for discount / pre-order offers.
 * @property percentageDiscount / [formattedDiscountAmount] Play supplies one of them for discounts.
 */
data class OfferInput(
    val offerToken: String?,
    val offerId: String?,
    val purchaseOptionId: String?,
    val formattedPrice: String,
    val priceMicros: Long,
    val currencyCode: String,
    val fullPriceMicros: Long?,
    val percentageDiscount: Int?,
    val formattedDiscountAmount: String?,
    val validFromMillis: Long?,
    val validUntilMillis: Long?,
    val isRental: Boolean,
    val isPreorder: Boolean,
)

/**
 * Picks the offer the paywall advertises and checkout uses.
 *
 * Play already returns only offers this user is eligible for (country, purchase limit, schedule),
 * so the job here is choosing among them and describing the choice honestly:
 *  - Rentals and pre-orders are skipped: Pro is a permanent, available-now unlock.
 *  - Offers outside their valid window are skipped (defensive; Play filters these too, but the
 *    paywall may outlive the query that returned them).
 *  - The cheapest remaining offer wins. On a tie the base offer wins, so a "discount" that saves
 *    nothing is never presented as one.
 *  - The struck-through price is always Play's own number, never invented.
 *
 * `LimitedQuantityInfo` is intentionally ignored: it is a per-user redemption cap, not stock, so
 * turning it into "only N left" would be a false scarcity claim.
 */
object OfferSelector {

    fun select(offers: List<OfferInput>, nowMillis: Long): ProOffer? {
        val candidates = offers.filter { it.isBuyableNow(nowMillis) }
        val chosen = candidates.minWithOrNull(
            compareBy<OfferInput> { it.priceMicros }.thenBy { if (it.offerId == null) 0 else 1 }
        ) ?: return null

        val isDiscount = chosen.fullPriceMicros != null && chosen.fullPriceMicros > chosen.priceMicros
        return ProOffer(
            offerToken = chosen.offerToken,
            formattedPrice = chosen.formattedPrice,
            priceMicros = chosen.priceMicros,
            currencyCode = chosen.currencyCode,
            fullFormattedPrice = if (isDiscount) fullPriceText(chosen, offers) else null,
            discountLabel = if (isDiscount) discountLabel(chosen) else null,
            endsAtMillis = chosen.validUntilMillis,
        )
    }

    private fun OfferInput.isBuyableNow(now: Long): Boolean =
        !isRental && !isPreorder &&
            (validFromMillis == null || validFromMillis <= now) &&
            (validUntilMillis == null || validUntilMillis > now)

    /**
     * Prefers the base offer's Play-formatted string (exact symbol placement and rounding Play uses
     * in the sheet); falls back to formatting the micros with the offer's currency.
     */
    private fun fullPriceText(offer: OfferInput, all: List<OfferInput>): String {
        val full = offer.fullPriceMicros!!
        all.firstOrNull {
            it.offerId == null && it.priceMicros == full && it.currencyCode == offer.currencyCode
        }?.let { return it.formattedPrice }
        return formatMicros(full, offer.currencyCode)
    }

    private fun discountLabel(offer: OfferInput): String? = when {
        offer.percentageDiscount != null && offer.percentageDiscount > 0 ->
            "Save ${offer.percentageDiscount}%"
        !offer.formattedDiscountAmount.isNullOrBlank() -> "Save ${offer.formattedDiscountAmount}"
        else -> {
            // Play gave neither; derive a percentage from the two prices it did give.
            val full = offer.fullPriceMicros ?: return null
            val pct = ((full - offer.priceMicros) * 100 / full).toInt()
            if (pct > 0) "Save $pct%" else null
        }
    }

    internal fun formatMicros(micros: Long, currencyCode: String): String {
        val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
        runCatching { format.currency = Currency.getInstance(currencyCode) }
        return format.format(micros / 1_000_000.0)
    }
}
