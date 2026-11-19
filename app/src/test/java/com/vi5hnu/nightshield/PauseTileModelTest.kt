package com.vi5hnu.nightshield

import com.vi5hnu.nightshield.PauseTileModel.Mode
import org.junit.Assert.assertEquals
import org.junit.Test

class PauseTileModelTest {

    private val oneMinute = FilterPause.Duration.MINUTE_1

    @Test fun filterOff_isUnavailable_evenIfAPauseLingers() {
        assertEquals(Mode.UNAVAILABLE, PauseTileModel.look(false, 0L, oneMinute).mode)
        assertEquals(Mode.UNAVAILABLE, PauseTileModel.look(false, 30_000L, oneMinute).mode)
    }

    @Test fun filterOn_isReady_andNamesTheChosenLength() {
        val look = PauseTileModel.look(true, 0L, FilterPause.Duration.MINUTES_5)
        assertEquals(Mode.READY, look.mode)
        assertEquals("For 5 min", look.subtitle)
    }

    @Test fun paused_countsDown() {
        val look = PauseTileModel.look(true, 45_000L, oneMinute)
        assertEquals(Mode.PAUSED, look.mode)
        assertEquals("Back in 0:45", look.subtitle)
    }

    @Test fun countdown_roundsUpAndFormatsMinutes() {
        assertEquals("0:01", PauseTileModel.countdown(1L))
        assertEquals("1:00", PauseTileModel.countdown(59_001L))
        assertEquals("4:05", PauseTileModel.countdown(245_000L))
    }
}
