package com.vi5hnu.nightshield

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.graphics.Color

/**
 * Monitors the foreground app and automatically adjusts the filter
 * based on per-app rules configured by the user.
 *
 * When a configured app comes to foreground:
 *  - If filterDisabled = true  → overlay is hidden (filter paused)
 *  - If customIntensity != null → overlay intensity is temporarily adjusted
 * When the app leaves foreground → global settings are restored.
 */
class NightShieldAccessibilityService : AccessibilityService() {

    private var lastPackage: String? = null
    // Stores global settings before any per-app override so we can restore them
    private var savedIntensity: Float? = null
    private var savedColor: Color? = null
    private var shakeHelper: ShakeHelper? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }

        // Bootstrap NightShieldManager from persisted prefs when the process
        // starts cold (i.e. the accessibility service is running but the app
        // was never opened).  Without this, appFilterConfigs is always empty
        // and the pause-filter feature never fires.
        bootstrapManagerIfNeeded()

        // Full shake toggle (on→off and off→on) via the always-running accessibility service
        shakeHelper = ShakeHelper(applicationContext) {
            if (!NightShieldManager.allowShake.value) return@ShakeHelper
            NightShieldManager.tryShakeToggle {
                if (OverlayHelpers.areOverlaysActive(applicationContext)) {
                    applicationContext.stopService(Intent(applicationContext, NightShieldService::class.java))
                    OverlayHelpers.setOverlaysActive(applicationContext, false)
                    NightShieldManager.setSleepTimer(0)
                } else if (OverlayHelpers.checkOverlayPermission(applicationContext)) {
                    OverlayHelpers.setOverlaysActive(applicationContext, true)
                    applicationContext.startForegroundService(Intent(applicationContext, NightShieldService::class.java))
                }
                NightShieldWidgetProvider.updateWidget(applicationContext)
            }
        }
        shakeHelper?.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Only react to window state changes (app switches)
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return

        // Ignore our own app, system UI, and keyboard / launcher overlays
        if (packageName == applicationContext.packageName) return
        if (packageName == "com.android.systemui") return
        if (packageName == lastPackage) return

        lastPackage = packageName
        applyRuleForPackage(packageName)
    }

    private fun applyRuleForPackage(packageName: String) {
        val config = NightShieldManager.appFilterConfigs.value[packageName]

        if (config != null) {
            // Entering a configured app — snapshot global settings once
            if (savedIntensity == null) {
                savedIntensity = NightShieldManager.filterIntensity.value
                savedColor = NightShieldManager.canvasColor.value
            }
            config.customIntensity?.let { NightShieldManager.setFilterIntensity(it) }
            // PRO: per-app custom color
            config.customColor?.let { NightShieldManager.setCanvasColor(it) }
            NightShieldManager.setFilterTemporarilyDisabled(config.filterDisabled)
        } else {
            // Leaving a configured app — restore everything
            restore()
        }
    }

    /**
     * Loads all persisted settings into [NightShieldManager] when the
     * process hasn't been through [MainActivity] yet.  Guards are cheap
     * (empty-map check) so calling this on every connect is safe.
     */
    private fun bootstrapManagerIfNeeded() {
        if (NightShieldManager.appFilterConfigs.value.isEmpty()) {
            NightShieldManager.setAppFilterConfigs(
                OverlayHelpers.loadAppConfigs(applicationContext)
            )
        }
        val (_, _, allowShake) = OverlayHelpers.loadFilterSettings(applicationContext)
        NightShieldManager.setAllowShake(allowShake)
        NightShieldManager.setShakeIntensity(
            OverlayHelpers.loadShakeIntensity(applicationContext)
        )
    }

    private fun restore() {
        NightShieldManager.setFilterTemporarilyDisabled(false)
        savedIntensity?.let { NightShieldManager.setFilterIntensity(it); savedIntensity = null }
        savedColor?.let { NightShieldManager.setCanvasColor(it); savedColor = null }
    }

    override fun onInterrupt() {
        restore()
    }

    override fun onDestroy() {
        super.onDestroy()
        shakeHelper?.stop()
        shakeHelper = null
        restore()
    }
}
