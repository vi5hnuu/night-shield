package com.vi5hnu.nightshield

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.android.gms.ads.MobileAds
import com.vi5hnu.nightshield.screens.HomeScreen
import com.vi5hnu.nightshield.screens.OnboardingScreen
import com.vi5hnu.nightshield.ui.theme.NightShieldTheme

class MainActivity : ComponentActivity() {

    private var hasOverlayPermission = mutableStateOf(false)
    private var areServicesRunning = mutableStateOf(false)

    private val prefs by lazy { getSharedPreferences("overlay_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize AdMob SDK (non-blocking)
        MobileAds.initialize(this)
        AppOpenAdManager.loadAd(this)

        hasOverlayPermission.value = OverlayHelpers.checkOverlayPermission(applicationContext)
        areServicesRunning.value = OverlayHelpers.areOverlaysActive(applicationContext)

        // Restore persisted settings into NightShieldManager
        val (color, intensity, allowShake) = OverlayHelpers.loadFilterSettings(applicationContext)
        NightShieldManager.setCanvasColor(color)
        NightShieldManager.setFilterIntensity(intensity)
        NightShieldManager.setAllowShake(allowShake)

        val schedules = OverlayHelpers.loadSchedules(applicationContext)
        NightShieldManager.setSchedules(schedules)

        val appConfigs = OverlayHelpers.loadAppConfigs(applicationContext)
        NightShieldManager.setAppFilterConfigs(appConfigs)

        // Auto-start service if filter was active
        if (hasOverlayPermission.value && areServicesRunning.value) {
            startOverlayService()
        }

        setContent {
            LaunchedEffect(areServicesRunning.value) {
                NightShieldWidgetProvider.updateWidget(applicationContext)
            }

            NightShieldTheme {
                var showOnboarding by remember {
                    mutableStateOf(!prefs.getBoolean("onboarding_complete", false))
                }

                if (showOnboarding) {
                    OnboardingScreen(onComplete = {
                        prefs.edit { putBoolean("onboarding_complete", true) }
                        showOnboarding = false
                    })
                } else {
                    HomeScreen(
                        onAllowShake = { NightShieldManager.setAllowShake(it) },
                        allowShake = NightShieldManager.allowShake.collectAsState().value,
                        areServicesActive = areServicesRunning.value,
                        hasOverlayPermission = hasOverlayPermission.value,
                        onPermissionRequest = { requestOverlayPermission() },
                        launchOverlays = { launchOverlays() },
                        stopOverlays = { stopOverlays() }
                    )
                }
            }
        }
    }

    private fun launchOverlays() {
        if (!hasOverlayPermission.value) return
        startOverlayService()
        OverlayHelpers.setOverlaysActive(this, true)
        areServicesRunning.value = true
    }

    private fun stopOverlays() {
        stopOverlayService()
        OverlayHelpers.setOverlaysActive(this, false)
        areServicesRunning.value = false
        NightShieldManager.setSleepTimer(0)
    }

    private fun requestOverlayPermission() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = "package:$packageName".toUri()
            }
        )
    }

    private fun startOverlayService() {
        startForegroundService(Intent(this, NightShieldService::class.java))
    }

    private fun stopOverlayService() {
        stopService(Intent(this, NightShieldService::class.java))
    }

    override fun onResume() {
        super.onResume()
        hasOverlayPermission.value = OverlayHelpers.checkOverlayPermission(applicationContext)
        val active = OverlayHelpers.areOverlaysActive(applicationContext)
        if (active && hasOverlayPermission.value) startOverlayService()
        areServicesRunning.value = active
        // Show App Open ad when returning to app (not on very first launch)
        AppOpenAdManager.showAdIfAvailable(this)
    }

    override fun onPause() {
        super.onPause()
        persistSettings()
    }

    private fun persistSettings() {
        OverlayHelpers.saveFilterSettings(
            this,
            NightShieldManager.canvasColor.value,
            NightShieldManager.filterIntensity.value,
            NightShieldManager.allowShake.value
        )
        OverlayHelpers.saveSchedules(this, NightShieldManager.schedules.value)
        OverlayHelpers.saveAppConfigs(this, NightShieldManager.appFilterConfigs.value)

        // Reschedule alarms with latest schedule list
        AlarmHelpers.scheduleAll(this, NightShieldManager.schedules.value)
    }
}
