package com.vi5hnu.nightshield

import android.content.Context
import androidx.core.content.edit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Tracks daily filter-on minutes for the Blue Light Report.
 *
 * [recordStart] is called when [NightShieldService] is created.
 * [recordStop]  is called when [NightShieldService] is destroyed.
 * [getWeeklyUsage] returns the last 7 days as (ISO-date, minutes) pairs.
 *
 * Storage: a dedicated SharedPreferences file keyed by date string.
 *
 * The session start is persisted, not held in memory: the filter typically runs all night, and an
 * OEM process kill used to throw the whole session away because the in-memory start was gone by the
 * time the restarted service stopped. Sessions crossing midnight are credited to each day they span.
 */
object UsageTracker {

    private const val PREFS = "usage_prefs"
    private const val PREFIX = "usage_"
    private const val KEY_SESSION_START = "session_start_ms"

    /** A persisted start older than this is treated as stale rather than a real session. */
    private const val MAX_SESSION_MS = 24 * 60 * 60 * 1000L

    /**
     * Mark the start of a filter session (call from NightShieldService.onCreate, before the
     * active flag is claimed).
     *
     * If the active flag is still set, the previous service instance was killed without
     * onDestroy and this is a START_STICKY restart of the same session — keep its start time.
     */
    fun recordStart(context: Context) {
        val prefs = context.usagePrefs()
        val now = System.currentTimeMillis()
        val existing = prefs.getLong(KEY_SESSION_START, 0L)
        val resuming = existing > 0L &&
            now - existing in 0..MAX_SESSION_MS &&
            OverlayHelpers.areOverlaysActive(context)
        if (!resuming) prefs.edit { putLong(KEY_SESSION_START, now) }
    }

    /** Mark the end of a filter session and persist duration (call from NightShieldService.onDestroy). */
    fun recordStop(context: Context) {
        val prefs = context.usagePrefs()
        val start = prefs.getLong(KEY_SESSION_START, 0L)
        prefs.edit { remove(KEY_SESSION_START) }
        if (start == 0L) return

        val now = System.currentTimeMillis()
        if (now - start !in 1..MAX_SESSION_MS) return

        val perDay = splitByDay(start, now, ZoneId.systemDefault())
        if (perDay.isEmpty()) return
        prefs.edit {
            perDay.forEach { (date, minutes) ->
                val key = "$PREFIX$date"
                putInt(key, prefs.getInt(key, 0) + minutes)
            }
        }

        // Update daily streak (uses OverlayHelpers which has its own prefs)
        OverlayHelpers.updateStreak(context)
    }

    /**
     * Splits [startMs, endMs) into whole minutes per local calendar day. Days that round to zero
     * minutes are omitted.
     */
    fun splitByDay(startMs: Long, endMs: Long, zone: ZoneId): Map<LocalDate, Int> {
        if (endMs <= startMs) return emptyMap()
        val result = linkedMapOf<LocalDate, Int>()
        var cursor = startMs
        var day = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate()
        while (cursor < endMs) {
            val nextMidnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val segmentEnd = minOf(nextMidnight, endMs)
            val minutes = ((segmentEnd - cursor) / 60_000).toInt()
            if (minutes > 0) result[day] = minutes
            cursor = segmentEnd
            day = day.plusDays(1)
        }
        return result
    }

    /**
     * Returns usage for the last 7 days, oldest-first.
     * Each entry is (ISO date string, minutes active).
     */
    fun getWeeklyUsage(context: Context): List<Pair<String, Int>> {
        val prefs = context.usagePrefs()
        val today = LocalDate.now()
        return (6 downTo 0).map { daysBack ->
            val date = today.minusDays(daysBack.toLong()).toString()
            date to prefs.getInt("$PREFIX$date", 0)
        }
    }

    /** Total minutes this week. */
    fun weeklyTotalMinutes(context: Context): Int =
        getWeeklyUsage(context).sumOf { it.second }

    /** Number of distinct days (all time) on which the filter was used. */
    fun activeDayCount(context: Context): Int =
        context.usagePrefs().all.count { (key, value) ->
            key.startsWith(PREFIX) && (value as? Int ?: 0) > 0
        }

    private fun Context.usagePrefs() =
        applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
