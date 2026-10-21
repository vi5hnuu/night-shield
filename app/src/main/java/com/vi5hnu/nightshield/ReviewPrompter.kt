package com.vi5hnu.nightshield

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.android.play.core.review.ReviewManagerFactory
import java.time.LocalDate

/**
 * Decides when to show Google Play's in-app review card.
 *
 * It used to fire once on a cold start after 7 days — the moment the user is trying to reach the
 * app, not the moment they are pleased with it. Now it is asked only right after a *value moment*
 * (see [Moment]) and only for users who have actually come to rely on the app:
 *
 *  - installed ≥ [MIN_INSTALL_DAYS] days and used on ≥ [MIN_ACTIVE_DAYS] distinct days,
 *  - ≥ [COOLDOWN_DAYS] since the last ask, at most [MAX_LIFETIME_ASKS] asks ever.
 *
 * Per Play policy nothing is asked first ("do you like the app?") — the card is shown as is. Play
 * also applies its own quota and may silently show nothing, so this is never wired to a button;
 * the Settings "Rate" row opens the store listing via [openStoreListing] instead.
 */
object ReviewPrompter {

    /** Points where the user has just got something they wanted from the app. */
    enum class Moment {
        /** Turned the filter on from the app. */
        FILTER_ON,
        /** Created a schedule — they are investing in the app. */
        SCHEDULE_ADDED,
        /** Just bought Pro — the strongest signal there is; skips the usage thresholds. */
        PURCHASED,
    }

    internal const val MIN_INSTALL_DAYS = 3
    internal const val MIN_ACTIVE_DAYS = 3
    internal const val COOLDOWN_DAYS = 60
    internal const val MAX_LIFETIME_ASKS = 3

    /** Lets the action's own UI (filter fading in, dialog closing) settle before the card. */
    private const val SHOW_DELAY_MS = 1_500L

    private const val PREFS = "review_prefs"
    private const val KEY_ASK_COUNT = "ask_count"
    private const val KEY_LAST_ASK_DAY = "last_ask_epoch_day"
    /** Pre-ReviewPrompter flag in overlay_prefs; migrated as one past ask. */
    private const val LEGACY_PREFS = "overlay_prefs"
    private const val LEGACY_KEY = "review_requested"

    fun onMoment(activity: Activity, moment: Moment) {
        migrateLegacy(activity)
        val prefs = activity.reviewPrefs()
        val today = LocalDate.now().toEpochDay()
        val eligible = isEligible(
            moment = moment,
            daysSinceInstall = OverlayHelpers.daysSinceFirstLaunch(activity),
            activeDays = UsageTracker.activeDayCount(activity),
            askCount = prefs.getInt(KEY_ASK_COUNT, 0),
            daysSinceLastAsk = prefs.getLong(KEY_LAST_ASK_DAY, -1L)
                .takeIf { it >= 0 }
                ?.let { (today - it).toInt() },
        )
        if (!eligible) return

        // Recorded up front: Play gives no signal of whether the card was shown, and counting
        // only on success could re-ask on every moment while Play's quota is hiding it.
        prefs.edit {
            putInt(KEY_ASK_COUNT, prefs.getInt(KEY_ASK_COUNT, 0) + 1)
            putLong(KEY_LAST_ASK_DAY, today)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (activity.isFinishing || activity.isDestroyed) return@postDelayed
            val manager = ReviewManagerFactory.create(activity)
            manager.requestReviewFlow().addOnSuccessListener { info ->
                if (!activity.isFinishing && !activity.isDestroyed) {
                    manager.launchReviewFlow(activity, info)
                }
            }
        }, SHOW_DELAY_MS)
    }

    /**
     * Pure eligibility rule (unit-tested).
     *
     * @param daysSinceLastAsk null if never asked.
     */
    internal fun isEligible(
        moment: Moment,
        daysSinceInstall: Int,
        activeDays: Int,
        askCount: Int,
        daysSinceLastAsk: Int?,
    ): Boolean {
        if (askCount >= MAX_LIFETIME_ASKS) return false
        if (daysSinceLastAsk != null && daysSinceLastAsk < COOLDOWN_DAYS) return false
        if (moment == Moment.PURCHASED) return true
        return daysSinceInstall >= MIN_INSTALL_DAYS && activeDays >= MIN_ACTIVE_DAYS
    }

    /** Opens the Play listing — for the explicit "Rate" row, where the review card can't be used. */
    fun openStoreListing(context: Context) {
        val pkg = context.packageName
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=$pkg".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$pkg".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun migrateLegacy(context: Context) {
        val legacy = context.applicationContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        if (!legacy.getBoolean(LEGACY_KEY, false)) return
        val prefs = context.reviewPrefs()
        if (!prefs.contains(KEY_ASK_COUNT)) {
            prefs.edit {
                putInt(KEY_ASK_COUNT, 1)
                putLong(KEY_LAST_ASK_DAY, LocalDate.now().toEpochDay())
            }
        }
        legacy.edit { remove(LEGACY_KEY) }
    }

    private fun Context.reviewPrefs() =
        applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
