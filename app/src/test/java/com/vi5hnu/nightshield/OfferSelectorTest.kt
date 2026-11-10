package com.vi5hnu.nightshield

import com.vi5hnu.nightshield.billing.OfferCopy
import com.vi5hnu.nightshield.billing.OfferInput
import com.vi5hnu.nightshield.billing.OfferSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferSelectorTest {

    private val now = 1_000_000L

    private fun base(price: Long = 49_000_000L) = OfferInput(
        offerToken = "base", offerId = null, purchaseOptionId = "buy",
        formattedPrice = "₹49.00", priceMicros = price, currencyCode = "INR",
        fullPriceMicros = null, percentageDiscount = null, formattedDiscountAmount = null,
        validUntilMillis = null, isRental = false, isPreorder = false,
    )

    private fun discount(
        price: Long = 29_000_000L,
        pct: Int? = 40,
        amount: String? = null,
        until: Long? = now + 86_400_000L,
        full: Long? = 49_000_000L,
    ) = base().copy(
        offerToken = "disc", offerId = "diwali", formattedPrice = "₹29.00", priceMicros = price,
        fullPriceMicros = full, percentageDiscount = pct, formattedDiscountAmount = amount,
        validUntilMillis = until,
    )

    @Test fun baseOnly_noDiscountShown() {
        val o = OfferSelector.select(listOf(base()))!!
        assertEquals("base", o.offerToken)
        assertFalse(o.isDiscounted)
        assertNull(o.discountLabel)
    }

    @Test fun percentageDiscount_isChosenWithStrikePriceFromBase() {
        val o = OfferSelector.select(listOf(base(), discount()))!!
        assertEquals("disc", o.offerToken)
        assertEquals("₹29.00", o.formattedPrice)
        assertEquals("₹49.00", o.fullFormattedPrice)
        assertEquals("Save 40%", o.discountLabel)
        assertEquals(now + 86_400_000L, o.endsAtMillis)
    }

    @Test fun absoluteDiscount_usesPlayFormattedAmount() {
        val o = OfferSelector.select(listOf(base(), discount(pct = null, amount = "₹20.00")))!!
        assertEquals("Save ₹20.00", o.discountLabel)
    }

    @Test fun noDiscountInfo_derivesPercentage() {
        val o = OfferSelector.select(listOf(base(), discount(pct = null)))!!
        assertEquals("Save 40%", o.discountLabel)
    }

    @Test fun tieWithBase_prefersBase() {
        val o = OfferSelector.select(listOf(discount(price = 49_000_000L), base()))!!
        assertEquals("base", o.offerToken)
        assertFalse(o.isDiscounted)
    }

    @Test fun offerWindow_isNotRefilteredWithDeviceClock() {
        // Play already applied the dates; a phone clock running ahead must not hide the discount.
        assertEquals("disc", OfferSelector.select(listOf(base(), discount(until = now)))!!.offerToken)
    }

    @Test fun offerId_isCarriedForStableIdentity() {
        assertEquals("diwali", OfferSelector.select(listOf(base(), discount()))!!.offerId)
    }

    @Test fun rentalAndPreorder_areExcluded() {
        val list = listOf(
            base(),
            discount().copy(offerToken = "rent", isRental = true, priceMicros = 1L),
            discount().copy(offerToken = "pre", isPreorder = true, priceMicros = 1L),
        )
        assertEquals("base", OfferSelector.select(list)!!.offerToken)
    }

    @Test fun missingFullPrice_isNotPresentedAsDiscount() {
        val o = OfferSelector.select(listOf(base(), discount(full = null)))!!
        assertEquals("disc", o.offerToken)
        assertFalse(o.isDiscounted)
        assertNull(o.discountLabel)
    }

    @Test fun noBaseInList_formatsFullPriceFromMicros() {
        val o = OfferSelector.select(listOf(discount()))!!
        assertTrue(o.isDiscounted)
        assertTrue(o.fullFormattedPrice!!.contains("49"))
    }

    @Test fun emptyList_returnsNull() {
        assertNull(OfferSelector.select(emptyList()))
    }
}

class OfferCopyTest {

    private val now = 1_700_000_000_000L
    private val h = 3_600_000L

    @Test fun noEndOrPast_returnsNull() {
        assertNull(OfferCopy.endsText(null, now))
        assertNull(OfferCopy.endsText(now, now))
    }

    @Test fun withinWeek_showsCountdown() {
        val copy = OfferCopy
        assertEquals("Offer ends in 2d 5h", copy.endsText(now + 53 * h, now))
        assertEquals("Offer ends in 3h 30m", copy.endsText(now + 3 * h + 30 * 60_000L, now))
        assertEquals("Offer ends in 1m", copy.endsText(now + 1_000L, now))
    }

    @Test fun beyondWeek_showsDate() {
        val text = OfferCopy.endsText(now + 10 * 24 * h, now, java.util.Locale.UK)
        assertTrue(text!!.startsWith("Offer ends ") && !text.contains(" in "))
    }

    @Test fun nextYearDate_includesYear() {
        val cal = java.util.Calendar.getInstance().apply { set(2026, java.util.Calendar.DECEMBER, 20, 12, 0) }
        val start = cal.timeInMillis
        val end = start + 40L * 24 * h
        assertTrue(OfferCopy.endsText(end, start, java.util.Locale.UK)!!.endsWith("2027"))
        assertFalse(OfferCopy.endsText(start + 10L * 24 * h, start - 20L * 24 * h, java.util.Locale.UK)!!.contains("202"))
    }
}

class OfferBannerStateTest {

    private fun offer(id: String?, ends: Long?) = com.vi5hnu.nightshield.billing.ProOffer(
        offerToken = "t", offerId = id, formattedPrice = "₹29", priceMicros = 29_000_000L,
        currencyCode = "INR", fullFormattedPrice = "₹49", discountLabel = "Save 40%", endsAtMillis = ends,
    )

    @Test fun sameOfferNewWindow_getsNewKey() {
        val key = com.vi5hnu.nightshield.billing.OfferBannerState::keyOf
        assertEquals(key(offer("diwali", 1L)), key(offer("diwali", 1L)))
        assertFalse(key(offer("diwali", 1L)) == key(offer("diwali", 2L)))
        assertFalse(key(offer("diwali", null)) == key(offer("newyear", null)))
    }
}
