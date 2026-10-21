package com.vi5hnu.nightshield

import org.junit.Assert.assertEquals
import org.junit.Test

class ProTrialTest {

    private val hour = 60 * 60 * 1000L
    private val day = ProTrial.DURATION_MS
    private val startWall = 1_800_000_000_000L
    private val startElapsed = 10 * hour
    private val boot = 7

    private fun state(maxSeen: Long = startWall) =
        ProTrial.State(startWall, startElapsed, boot, maxSeen)

    private fun remaining(
        nowWall: Long,
        nowElapsed: Long,
        bootCount: Int = boot,
        st: ProTrial.State? = state(),
    ) = ProTrial.remainingMs(st, nowWall, nowElapsed, bootCount)

    @Test
    fun noTrialMeansNothingLeft() = assertEquals(0L, remaining(startWall, startElapsed, st = null))

    @Test
    fun sameBootCountsUptimeNotTheClock() {
        // 2 h of uptime passed, but the user wound the date back 10 h: still 22 h left, not 32.
        assertEquals(day - 2 * hour, remaining(startWall + 2 * hour, startElapsed + 2 * hour))
    }

    @Test
    fun movingTheClockForwardDoesNotHelpEither() {
        assertEquals(day - hour, remaining(startWall + 30 * day, startElapsed + hour, st = state()))
    }

    @Test
    fun expiresAfterADayOfUptime() = assertEquals(0L, remaining(startWall + day, startElapsed + day))

    @Test
    fun afterRebootFallsBackToTheWallClock() {
        assertEquals(day - 5 * hour, remaining(startWall + 5 * hour, nowElapsed = hour, bootCount = boot + 1))
    }

    @Test
    fun clockSetBeforeTheStartEndsTheTrial() =
        assertEquals(0L, remaining(startWall - hour, hour, bootCount = boot + 1))

    @Test
    fun clockSetBehindTheLatestSeenTimeEndsTheTrial() {
        val seen = state(maxSeen = startWall + 6 * hour)
        assertEquals(0L, remaining(startWall + 2 * hour, hour, bootCount = boot + 1, st = seen))
    }

    @Test
    fun smallClockCorrectionsAreTolerated() {
        val seen = state(maxSeen = startWall + 6 * hour)
        val corrected = startWall + 6 * hour - ProTrial.CLOCK_TOLERANCE_MS / 2
        assertEquals(day - (corrected - startWall), remaining(corrected, hour, bootCount = boot + 1, st = seen))
    }

    @Test
    fun unknownBootCountUsesTheWallClock() {
        val st = ProTrial.State(startWall, startElapsed, bootCount = -1, maxSeenWall = startWall)
        assertEquals(day - 3 * hour, remaining(startWall + 3 * hour, startElapsed + 3 * hour, bootCount = -1, st = st))
    }
}
