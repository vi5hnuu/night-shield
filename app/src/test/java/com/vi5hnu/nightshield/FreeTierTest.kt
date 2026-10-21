package com.vi5hnu.nightshield

import org.junit.Assert.assertEquals
import org.junit.Test

class FreeTierTest {

    private val rules = listOf(
        AppFilterConfig("com.zeta", appLabel = "Zeta"),
        AppFilterConfig("com.alpha", appLabel = "alpha"),
        AppFilterConfig("com.mid", appLabel = "Mid"),
    ).associateBy { it.packageName }

    @Test
    fun proKeepsEveryRule() = assertEquals(rules, FreeTier.activeAppRules(rules, isPro = true))

    @Test
    fun freeKeepsTheFirstRuleByLabelCaseInsensitively() =
        assertEquals(setOf("com.alpha"), FreeTier.activeAppRules(rules, isPro = false).keys)

    @Test
    fun freeSelectionIsIndependentOfMapOrder() {
        val reversed = rules.entries.reversed().associate { it.key to it.value }
        assertEquals(
            FreeTier.activeAppRules(rules, isPro = false),
            FreeTier.activeAppRules(reversed, isPro = false),
        )
    }

    @Test
    fun schedulesAreCappedForFreeOnly() {
        val schedules = (0..2).map { ScheduleEntry(hour = 20 + it, minute = 0, action = ScheduleAction.ON) }
        assertEquals(FreeTier.MAX_SCHEDULES, FreeTier.activeSchedules(schedules, isPro = false).size)
        assertEquals(3, FreeTier.activeSchedules(schedules, isPro = true).size)
    }
}
