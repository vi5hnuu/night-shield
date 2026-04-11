package com.vi5hnu.nightshield

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Fires once a week to remind the user how many hours Night Shield protected their eyes.
 * Battery-safe: WorkManager delivers the job opportunistically with KEEP policy
 * and a "battery not low" constraint, so it never wakes up the device just for this.
 */
class WeeklyReportWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        val usage = UsageTracker.getWeeklyUsage(applicationContext)
        val totalMins = usage.sumOf { it.second }
        // Don't nag the user if they barely used it
        if (totalMins < 5) return Result.success()

        val timeStr = if (totalMins >= 60) {
            val h = totalMins / 60; val m = totalMins % 60
            if (m > 0) "${h}h ${m}m" else "${h}h"
        } else "${totalMins}m"

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Weekly Eye Report", NotificationManager.IMPORTANCE_LOW)
        )

        val openIntent = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        nm.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_24)
                .setContentTitle("Your eyes this week")
                .setContentText("Night Shield protected you for $timeStr — great habit!")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("Night Shield was active for $timeStr this week, reducing your blue light exposure every day you used it. Keep it up!"))
                .setContentIntent(openIntent)
                .setAutoCancel(true)
                .build()
        )
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "weekly_report_channel"
        private const val NOTIFICATION_ID = 5678
        private const val WORK_NAME = "weekly_eye_report"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeeklyReportWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(7, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
