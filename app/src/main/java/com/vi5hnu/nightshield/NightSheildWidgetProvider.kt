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

            // Tapping the status pill opens the app (the shield icon toggles the filter).
            // Only shown in STANDARD / DETAILED styles — MINIMAL hides the strip, so it stays
            // toggle-only there.
            val openAppIntent = PendingIntent.getActivity(
                context, 100,
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetTextStrip, openAppIntent)

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
        val action = intent.action ?: return
        val isEnabled = action == AppWidgetManager.ACTION_APPWIDGET_ENABLED
        val customSignal = action == TOGGLE_SHIELD_SIGNAL
        if (!isEnabled && !customSignal) return
        // Toggle through the single controller — the service owns the active flag and refreshes
        // the widget on its transition. The refresh below covers the non-toggle (enable) path.
        if (customSignal) NightShieldController.toggle(context)
        updateWidget(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidget(context);//update all at once
    }
}
