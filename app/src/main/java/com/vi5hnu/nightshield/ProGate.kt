package com.vi5hnu.nightshield

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for pro entitlement.
 *
 * Two independent sources feed it:
 *  - [BillingManager] calls [grant] / [revoke] for the paid one-time purchase.
 *  - [ProTrial] calls [setTrialActive] for the 24-hour trial earned by watching a rewarded ad.
 *
 * [isPro] — "Pro features are unlocked" (paid OR trial). Every feature gate reads this.
 * [isPaid] — "the user bought Pro". Ads, the Pro badge and the paywall read this: a trial unlocks
 * features but keeps ads, so going ad-free stays the reason to buy.
 *
 * Kept separate so a Play re-check that finds no purchase cannot cancel an earned trial, and a
 * trial ending cannot revoke a purchase.
 */
object ProGate {
    private var paid = false
    private var trial = false

    private val _isPaid = MutableStateFlow(false)
    val isPaid: StateFlow<Boolean> = _isPaid.asStateFlow()

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    fun grant() { paid = true; publish() }
    fun revoke() { paid = false; publish() }

    fun setTrialActive(active: Boolean) { trial = active; publish() }

    @Synchronized
    private fun publish() {
        _isPaid.value = paid
        _isPro.value = paid || trial
    }
}
