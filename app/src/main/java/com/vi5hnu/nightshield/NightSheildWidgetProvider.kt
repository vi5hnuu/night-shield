package com.vi5hnu.nightshield

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

class NightShieldWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TOGGLE_SHIELD_SIGNAL = "com.vi5hnu.nightshield.TOGGLE_SHIELD_SIGNAL"

        fun updateWidget(context: Context) {
            val views = RemoteViews(context.packageName, R.layout.night_shield_widget_layout)

            val intent = Intent().apply {
                action = TOGGLE_SHIELD_SIGNAL
                setClass(context, NightShieldWidgetProvider::class.java)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val isRunning = OverlayHelpers.areOverlaysActive(context)
            views.setImageViewResource(
                R.id.shieldAction,
                if (isRunning) R.drawable.shield_active else R.drawable.shield_inactive
            )
            views.setOnClickPendingIntent(R.id.shieldAction, pendingIntent)

            // Apply widget style — floating pill on icon, no harsh strip
            val style = OverlayHelpers.loadWidgetStyle(context)
            when (style) {
                NightShieldManager.WidgetStyle.MINIMAL -> {
                    views.setViewVisibility(R.id.widgetTextStrip, View.GONE)
                    views.setViewVisibility(R.id.widgetStatusText, View.GONE)
                    views.setViewVisibility(R.id.widgetSeparator, View.GONE)
                    views.setViewVisibility(R.id.widgetIntensityText, View.GONE)
                }
                NightShieldManager.WidgetStyle.STANDARD -> {
                    views.setViewVisibility(R.id.widgetTextStrip, View.VISIBLE)
                    views.setViewVisibility(R.id.widgetStatusText, View.VISIBLE)
                    views.setTextViewText(R.id.widgetStatusText, if (isRunning) "● ON" else "○ OFF")
                    views.setViewVisibility(R.id.widgetSeparator, View.GONE)
                    views.setViewVisibility(R.id.widgetIntensityText, View.GONE)
                }
                NightShieldManager.WidgetStyle.DETAILED -> {
                    val intensityPct = (OverlayHelpers.loadFilterSettings(context).second * 100).toInt()
                    views.setViewVisibility(R.id.widgetTextStrip, View.VISIBLE)
                    views.setViewVisibility(R.id.widgetStatusText, View.VISIBLE)
                    views.setTextViewText(R.id.widgetStatusText, if (isRunning) "ON" else "OFF")
                    views.setViewVisibility(R.id.widgetSeparator, View.VISIBLE)
                    views.setViewVisibility(R.id.widgetIntensityText, View.VISIBLE)
                    views.setTextViewText(R.id.widgetIntensityText, "$intensityPct%")
                }
            }

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, NightShieldWidgetProvider::class.java)
            appWidgetManager.updateAppWidget(thisWidget, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val isEnabled=intent.action==AppWidgetManager.ACTION_APPWIDGET_ENABLED;
        val customSignal=intent.action==TOGGLE_SHIELD_SIGNAL;
        if (!(intent.action!=null && (isEnabled || customSignal))) return;
        val isRunning = OverlayHelpers.areOverlaysActive(context)
        if(customSignal){
            if (isRunning) {
                context.stopService(Intent(context, NightShieldService::class.java))
                OverlayHelpers.setOverlaysActive(context, false)
            } else {
                context.startForegroundService(Intent(context, NightShieldService::class.java))
                OverlayHelpers.setOverlaysActive(context, true)
            }
        }
        updateWidget(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidget(context);//update all at once
    }
}
