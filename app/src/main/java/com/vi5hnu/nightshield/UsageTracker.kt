package com.vi5hnu.nightshield

import android.content.Context
import java.time.LocalDate

/**
 * Tracks daily filter-on minutes for the Blue Light Report.
 *
 * [recordStart] is called when [NightShieldService] is created.
 * [recordStop]  is called when [NightShieldService] is destroyed.
 * [getWeeklyUsage] returns the last 7 days as (ISO-date, minutes) pairs.
 *
 * Storage: a dedicated SharedPreferences file keyed by date string.
 */
object UsageTracker {

    private const val PREFS = "usage_prefs"
    private const val PREFIX = "usage_"

    private var sessionStart = 0L

    /** Mark the start of a filter session (call from NightShieldService.onCreate). */
    fun recordStart() {
        sessionStart = System.currentTimeMillis()
    }

    /** Mark the end of a filter session and persist duration (call from NightShieldService.onDestroy). */
    fun recordStop(context: Context) {
        val start = sessionStart
        sessionStart = 0L
        if (start == 0L) return

        val minutes = ((System.currentTimeMillis() - start) / 60_000).toInt()
        if (minutes <= 0) return

        val today = LocalDate.now().toString()          // "2025-04-11"
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getInt("$PREFIX$today", 0)
        prefs.edit().putInt("$PREFIX$today", existing + minutes).apply()
    }

    /**
     * Returns usage for the last 7 days, oldest-first.
     * Each entry is (ISO date string, minutes active).
     */
    fun getWeeklyUsage(context: Context): List<Pair<String, Int>> {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now()
        return (6 downTo 0).map { daysBack ->
            val date = today.minusDays(daysBack.toLong()).toString()
            date to prefs.getInt("$PREFIX$date", 0)
        }
    }

    /** Total minutes this week. */
    fun weeklyTotalMinutes(context: Context): Int =
        getWeeklyUsage(context).sumOf { it.second }
}
