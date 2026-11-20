package com.vi5hnu.nightshield

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Re-checks the Pro purchase with Google Play about once a day, whenever the phone is online.
 *
 * Purchases were otherwise re-checked only when the app opened or its process started. The filter
 * service can run for days and most control happens from the notification, widget or tile, so:
 *  - a refunded purchase could keep Pro unlocked indefinitely, and
 *  - a slow payment (UPI, cash) that cleared while the app was closed went unacknowledged, and Play
 *    refunds an unacknowledged purchase after three days.
 *
 * Fair to genuine buyers: it requires a connection, so it simply waits while the phone is offline,
 * and a check that fails never removes Pro ([BillingManager.recheck]). There is no offline time
 * limit. It always reports success, so a failed check waits for the next daily run instead of
 * retrying and draining the battery.
 */
class PurchaseRecheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        BillingManager.recheck(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "purchase_recheck"

        /** Idempotent (KEEP), so it is safe to call on every process start. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PurchaseRecheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
