package com.vi5hnu.nightshield

import android.app.Application

/**
 * Process-wide bootstrap.
 *
 * Android creates this before any component — including a broadcast receiver woken by an alarm,
 * a Tasker intent or a widget tap in an otherwise dead process. Those receivers read
 * [ProGate.isPro] synchronously, and without this they saw `false` for a paying user (the gate was
 * only filled in by MainActivity / the services), which silently dropped Pro behaviour such as the
 * auto sunset/sunrise midnight recompute.
 *
 * Only the cached entitlement (and the locally stored rewarded trial) is applied here — no billing connection is opened. [BillingManager]
 * still verifies with Play whenever the UI or a service initialises it.
 */
class NightShieldApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BillingManager.isProCached(this)) ProGate.grant()
        // Ad-earned trial: restore it, or apply free limits if it ran out while we were dead.
        ProTrial.reconcile(this)
    }
}
