package com.vi5hnu.nightshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class UsageTrackerTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun ms(dt: LocalDateTime) = dt.atZone(zone).toInstant().toEpochMilli()

    @Test
    fun sameDaySessionIsCreditedToThatDay() {
        val start = ms(LocalDateTime.of(2026, 10, 20, 21, 0))
        val end = ms(LocalDateTime.of(2026, 10, 20, 22, 30))
        assertEquals(mapOf(LocalDate.of(2026, 10, 20) to 90), UsageTracker.splitByDay(start, end, zone))
    }

    @Test
    fun overnightSessionIsSplitAtMidnight() {
        val start = ms(LocalDateTime.of(2026, 10, 20, 23, 0))
        val end = ms(LocalDateTime.of(2026, 10, 21, 6, 15))
        assertEquals(
            mapOf(LocalDate.of(2026, 10, 20) to 60, LocalDate.of(2026, 10, 21) to 375),
            UsageTracker.splitByDay(start, end, zone),
        )
    }

    @Test
    fun emptyOrReversedRangeYieldsNothing() {
        val t = ms(LocalDateTime.of(2026, 10, 20, 23, 0))
        assertTrue(UsageTracker.splitByDay(t, t, zone).isEmpty())
        assertTrue(UsageTracker.splitByDay(t, t - 1, zone).isEmpty())
    }

    @Test
    fun subMinuteSegmentsAreDropped() {
        val start = ms(LocalDateTime.of(2026, 10, 20, 23, 59, 30))
        val end = ms(LocalDateTime.of(2026, 10, 21, 0, 10))
        assertEquals(mapOf(LocalDate.of(2026, 10, 21) to 10), UsageTracker.splitByDay(start, end, zone))
    }
}
