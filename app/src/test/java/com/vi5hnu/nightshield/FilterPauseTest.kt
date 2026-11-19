package com.vi5hnu.nightshield

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterPauseTest {

    @Test fun deadline_addsChosenDuration() {
        assertEquals(61_000L, FilterPause.deadline(1_000L, FilterPause.Duration.MINUTE_1))
        assertEquals(301_000L, FilterPause.deadline(1_000L, FilterPause.Duration.MINUTES_5))
    }

    @Test fun remaining_isZeroWhenNotPausedOrPastDeadline() {
        assertEquals(0L, FilterPause.remainingMs(0L, 5_000L))
        assertEquals(0L, FilterPause.remainingMs(4_000L, 5_000L))
        assertEquals(2_500L, FilterPause.remainingMs(7_500L, 5_000L))
    }

    @Test fun unknownStoredDuration_fallsBackToDefault() {
        assertEquals(FilterPause.Duration.MINUTE_1, FilterPause.Duration.fromSeconds(999))
        assertEquals(FilterPause.Duration.SECONDS_30, FilterPause.Duration.fromSeconds(30))
    }
}
