package com.vi5hnu.nightshield

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Home screen widget with − / + buttons to nudge the filter intensity by 10 percentage points.
 * Buttons broadcast to [IntensityActionReceiver]; that receiver persists the value and calls back
 * into [updateAll] to refresh the displayed percentage.
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

            views.setOnClickPendingIntent(R.id.intensityMinus, action(context, IntensityActionReceiver.ACTION_DECREASE, 20))
            views.setOnClickPendingIntent(R.id.intensityPlus, action(context, IntensityActionReceiver.ACTION_INCREASE, 21))

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
