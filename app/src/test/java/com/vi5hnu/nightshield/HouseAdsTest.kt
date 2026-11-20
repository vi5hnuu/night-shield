package com.vi5hnu.nightshield

import com.vi5hnu.nightshield.HouseAds.UPSELL_MIN_INTERVAL_MS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The daily cap on the upgrade note that replaces an interstitial that couldn't load. */
class HouseAdsTest {

    private val now = 1_800_000_000_000L

    @Test
    fun `first ever note is allowed`() {
        assertTrue(HouseAds.upsellAllowed(now, lastMs = 0L))
    }

    @Test
    fun `a second note within a day is not allowed`() {
        assertFalse(HouseAds.upsellAllowed(now, lastMs = now - 60_000L))
        assertFalse(HouseAds.upsellAllowed(now, lastMs = now - UPSELL_MIN_INTERVAL_MS + 1))
    }

    @Test
    fun `a note is allowed again once a full day has passed`() {
        assertTrue(HouseAds.upsellAllowed(now, lastMs = now - UPSELL_MIN_INTERVAL_MS))
    }

    @Test
    fun `a clock moved backwards does not block notes forever`() {
        assertTrue(HouseAds.upsellAllowed(now, lastMs = now + 365L * UPSELL_MIN_INTERVAL_MS))
    }
}
