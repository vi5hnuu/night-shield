package com.vi5hnu.nightshield

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Compact filter-control widget: a shield toggle (on/off), − / + to nudge the intensity by 10 pp,
 * and the current % (tap to open the app). The −/+ go to [IntensityActionReceiver]; the toggle
 * reuses the exported [NightShieldWidgetProvider] toggle signal.
 */
class IntensityWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context)
    }

    companion object {
        fun updateAll(context: Context) {
            val views = RemoteViews(context.packageName, R.layout.intensity_widget_layout)

            val pct = (OverlayHelpers.loadFilterSettings(context).second * 100).toInt()
            views.setTextViewText(R.id.intensityValue, "$pct%")

            val isRunning = OverlayHelpers.areOverlaysActive(context)
            views.setImageViewResource(
                R.id.intensityToggle,
                if (isRunning) R.drawable.shield_active else R.drawable.shield_inactive
            )

            // Toggle the filter via the exported widget provider's signal.
            views.setOnClickPendingIntent(R.id.intensityToggle, PendingIntent.getBroadcast(
                context, 22,
                Intent(context, NightShieldWidgetProvider::class.java)
                    .setAction(NightShieldWidgetProvider.TOGGLE_SHIELD_SIGNAL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            views.setOnClickPendingIntent(R.id.intensityMinus, action(context, IntensityActionReceiver.ACTION_DECREASE, 20))
            views.setOnClickPendingIntent(R.id.intensityPlus, action(context, IntensityActionReceiver.ACTION_INCREASE, 21))
            // Tap the % to open the app.
            views.setOnClickPendingIntent(R.id.intensityValue, PendingIntent.getActivity(
                context, 23,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))

            val mgr = AppWidgetManager.getInstance(context)
            mgr.updateAppWidget(ComponentName(context, IntensityWidgetProvider::class.java), views)
        }

        private fun action(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, requestCode,
                Intent(context, IntensityActionReceiver::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
