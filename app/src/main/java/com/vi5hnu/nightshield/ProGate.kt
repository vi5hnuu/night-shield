package com.vi5hnu.nightshield

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for pro entitlement.
 * [BillingManager] calls [grant] after a successful purchase or verified restore.
 * All UI reads [isPro] to gate premium features.
 */
object ProGate {
    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    fun grant() { _isPro.value = true }
    fun revoke() { _isPro.value = false }
}
