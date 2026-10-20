package com.vi5hnu.nightshield

/**
 * The single definition of what a free user gets.
 *
 * Limits used to be repeated as literals in the screens, the boot receiver, backup import and the
 * refund path, which is how they drifted apart. Everything that caps or filters for free users
 * reads from here.
 *
 * Caps are applied at *runtime* rather than by deleting data: a Pro user who reinstalls is briefly
 * "free" until Play answers, and trimming then would destroy settings they paid for.
 */
object FreeTier {

    /** Fixed-time schedules a free user can have armed. */
    const val MAX_SCHEDULES = 1

    /** Per-app rules a free user can have applied. */
    const val MAX_APP_RULES = 1

    /** Schedules that may actually be armed. */
    fun activeSchedules(schedules: List<ScheduleEntry>, isPro: Boolean): List<ScheduleEntry> =
        if (isPro) schedules else schedules.take(MAX_SCHEDULES)

    /**
     * Per-app rules that are in effect.
     *
     * Ordered by label (then package) so the rule a free user keeps is stable across launches —
     * the backing map is rebuilt from an unordered set on every load.
     */
    fun activeAppRules(
        configs: Map<String, AppFilterConfig>,
        isPro: Boolean,
    ): Map<String, AppFilterConfig> {
        if (isPro) return configs
        return configs.values
            .sortedWith(compareBy({ it.appLabel.lowercase() }, { it.packageName }))
            .take(MAX_APP_RULES)
            .associateBy { it.packageName }
    }
}
