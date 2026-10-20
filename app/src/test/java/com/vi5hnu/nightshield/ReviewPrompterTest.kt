package com.vi5hnu.nightshield

import com.vi5hnu.nightshield.ReviewPrompter.Moment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPrompterTest {

    private fun eligible(
        moment: Moment = Moment.FILTER_ON,
        installDays: Int = 10,
        activeDays: Int = 5,
        asks: Int = 0,
        sinceLast: Int? = null,
    ) = ReviewPrompter.isEligible(moment, installDays, activeDays, asks, sinceLast)

    @Test
    fun engagedUserIsAsked() = assertTrue(eligible())

    @Test
    fun newInstallIsNotAsked() = assertFalse(eligible(installDays = 1))

    @Test
    fun lightUserIsNotAsked() = assertFalse(eligible(activeDays = 2))

    @Test
    fun cooldownIsRespected() {
        assertFalse(eligible(asks = 1, sinceLast = ReviewPrompter.COOLDOWN_DAYS - 1))
        assertTrue(eligible(asks = 1, sinceLast = ReviewPrompter.COOLDOWN_DAYS))
    }

    @Test
    fun lifetimeCapIsRespected() =
        assertFalse(eligible(asks = ReviewPrompter.MAX_LIFETIME_ASKS, sinceLast = 365))

    @Test
    fun purchaseSkipsUsageThresholdsButNotCooldown() {
        assertTrue(eligible(Moment.PURCHASED, installDays = 0, activeDays = 0))
        assertFalse(eligible(Moment.PURCHASED, installDays = 0, activeDays = 0, asks = 1, sinceLast = 5))
    }
}
