package com.vi5hnu.nightshield

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.edit
import com.google.android.gms.ads.MobileAds
import com.google.android.play.core.review.ReviewManagerFactory
import com.vi5hnu.nightshield.screens.HomeScreen
import com.vi5hnu.nightshield.screens.OnboardingScreen
import com.vi5hnu.nightshield.ui.theme.NightShieldTheme

class MainActivity : ComponentActivity() {

    private var hasOverlayPermission = mutableStateOf(false)

    private val prefs by lazy { getSharedPreferences("overlay_prefs", MODE_PRIVATE) }

    // ── Backup: export JSON to a user-chosen file ─────────────────────────────
    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        runCatching {
            val json = BackupHelper.export(this)
            contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            Toast.makeText(this, "Settings exported successfully", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "Export failed: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ── Restore: import JSON from a user-chosen file ──────────────────────────
    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        runCatching {
            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                ?: error("Could not read file")
            if (BackupHelper.import(this, json)) {
                Toast.makeText(this, "Settings restored successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Invalid backup file", Toast.LENGTH_LONG).show()
            }
        }.onFailure {
            Toast.makeText(this, "Restore failed: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        MobileAds.initialize(this)
        AppOpenAdManager.loadAd(this)
        InterstitialAdManager.loadAd(this)

        // ── First-launch tracking ─────────────────────────────────────────────
        OverlayHelpers.ensureFirstLaunchRecorded(this)

        // ── Weekly eye-report notification ────────────────────────────────────
        WeeklyReportWorker.schedule(this)

        // ── Billing ───────────────────────────────────────────────────────────
        BillingManager.init(this)

        // ── Restore persisted settings into NightShieldManager ───────────────
        hasOverlayPermission.value = OverlayHelpers.checkOverlayPermission(applicationContext)
        // Seed the reactive active-state flow from the durable flag (the service keeps it updated).
        NightShieldManager.setFilterActive(OverlayHelpers.areOverlaysActive(applicationContext))

        val (color, intensity, allowShake) = OverlayHelpers.loadFilterSettings(applicationContext)
        NightShieldManager.setCanvasColor(color)
        NightShieldManager.setFilterIntensity(intensity)
        NightShieldManager.setAllowShake(allowShake)
        NightShieldManager.setShakeIntensity(OverlayHelpers.loadShakeIntensity(applicationContext))
        NightShieldManager.setGradualFadeEnabled(OverlayHelpers.loadGradualFade(applicationContext) && ProGate.isPro.value)
        NightShieldManager.setDimLevel(OverlayHelpers.loadDimLevel(applicationContext))
        NightShieldManager.setAppTheme(OverlayHelpers.loadAppTheme(applicationContext))
        NightShieldManager.setWidgetStyle(OverlayHelpers.loadWidgetStyle(applicationContext))

        NightShieldManager.setSchedules(OverlayHelpers.loadSchedules(applicationContext))
        NightShieldManager.setAppFilterConfigs(OverlayHelpers.loadAppConfigs(applicationContext))
        NightShieldManager.setProfiles(OverlayHelpers.loadProfiles(applicationContext))
        NightShieldManager.setEyeBreakEnabled(OverlayHelpers.loadEyeBreakEnabled(applicationContext))
        NightShieldManager.setDarkModeAutoSync(OverlayHelpers.loadDarkModeSync(applicationContext))

        // Reconcile: restart the service only if the flag says active, we have permission, and the
        // service isn't already alive (recovers from a process-kill without restarting during the
        // brief stopService→onDestroy gap).
        if (hasOverlayPermission.value &&
            OverlayHelpers.areOverlaysActive(applicationContext) &&
            !NightShieldService.isRunning
        ) startOverlayService()
        // Start/stop the background shake monitor per the single rule (shake on + filter off + perm).
        NightShieldController.syncShakeMonitor(applicationContext)

        setContent {
            val isFilterActive by NightShieldManager.isFilterActive.collectAsState()
            val areServicesActive = isFilterActive && hasOverlayPermission.value

            LaunchedEffect(areServicesActive) {
                NightShieldWidgetProvider.updateWidget(applicationContext)
            }

            // Observe theme changes so the whole UI reflects the chosen theme live
            val appTheme by NightShieldManager.appTheme.collectAsState()
            val isPro    by ProGate.isPro.collectAsState()

            // In-app review: request after 7 days + ≥20 min total filter time
            LaunchedEffect(Unit) {
                val days  = OverlayHelpers.daysSinceFirstLaunch(applicationContext)
                val mins  = UsageTracker.weeklyTotalMinutes(applicationContext)
                val shown = prefs.getBoolean("review_requested", false)
                if (!shown && days >= 7 && mins >= 20) {
                    val manager = ReviewManagerFactory.create(this@MainActivity)
                    manager.requestReviewFlow().addOnSuccessListener { info ->
                        manager.launchReviewFlow(this@MainActivity, info)
                        prefs.edit { putBoolean("review_requested", true) }
                    }
                }
            }

            NightShieldTheme(theme = appTheme) {
                var showOnboarding by remember {
                    mutableStateOf(!prefs.getBoolean("onboarding_complete", false))
                }

                // Contextual upgrade prompt: show once after 3 days OR 30 min of filter use
                val triggerUpgrade = remember(isPro) {
                    val days  = OverlayHelpers.daysSinceFirstLaunch(applicationContext)
                    val mins  = UsageTracker.weeklyTotalMinutes(applicationContext)
                    !isPro && !OverlayHelpers.isUpgradePromptShown(applicationContext) &&
                        (days >= 3 || mins >= 30)
                }

                if (showOnboarding) {
                    OnboardingScreen(onComplete = {
                        prefs.edit { putBoolean("onboarding_complete", true) }
                        showOnboarding = false
                    })
                } else {
                    HomeScreen(
                        onAllowShake         = {
                            NightShieldManager.setAllowShake(it)
                            OverlayHelpers.saveFilterSettings(
                                applicationContext,
                                NightShieldManager.canvasColor.value,
                                NightShieldManager.filterIntensity.value,
                                it,
                            )
                            // Shake toggle changed — start or stop the background monitor.
                            NightShieldController.syncShakeMonitor(applicationContext)
                        },
                        allowShake           = NightShieldManager.allowShake.collectAsState().value,
                        areServicesActive    = areServicesActive,
                        hasOverlayPermission = hasOverlayPermission.value,
                        onPermissionRequest  = { requestOverlayPermission() },
                        launchOverlays       = { launchOverlays() },
                        stopOverlays         = { stopOverlays() },
                        isPro                = isPro,
                        triggerUpgradePrompt = triggerUpgrade,
                        onUpgradePromptShown = { OverlayHelpers.markUpgradePromptShown(applicationContext) },
                        onPurchase           = { BillingManager.purchase(this@MainActivity) },
                        onRestorePurchase    = { BillingManager.restore(this@MainActivity) },
                        onExportSettings     = { createDocumentLauncher.launch("nightshield_backup.json") },
                        onImportSettings     = { openDocumentLauncher.launch(arrayOf("application/json")) },
                    )
                }
            }
        }
    }

    private fun launchOverlays() {
        // The service flips the durable flag + widget; optimistically mirror the flow for
        // instant button feedback. The service confirms (or self-corrects if it can't start).
        if (NightShieldController.activate(this)) NightShieldManager.setFilterActive(true)
    }

    private fun stopOverlays() {
        NightShieldController.deactivate(this)
        NightShieldManager.setFilterActive(false)
        NightShieldManager.setSleepTimer(0)
        InterstitialAdManager.onFilterStopped(this)
    }

    private fun requestOverlayPermission() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
        )
    }

    private fun startOverlayService() {
        startForegroundService(Intent(this, NightShieldService::class.java))
    }

    override fun onResume() {
        super.onResume()
        hasOverlayPermission.value = OverlayHelpers.checkOverlayPermission(applicationContext)
        val active = OverlayHelpers.areOverlaysActive(applicationContext)
        // Reconcile: if the flag says active and we have permission but the service isn't alive,
        // it died to a process-kill — restart it. The !isRunning guard avoids restarting the
        // filter during the brief stopService→onDestroy window (e.g. user taps OFF then reopens).
        if (active && hasOverlayPermission.value && !NightShieldService.isRunning) startOverlayService()
        val running = active && hasOverlayPermission.value
        NightShieldManager.setFilterActive(running)

        // Dark mode auto-sync: auto-enable filter when system switches to dark mode
        if (NightShieldManager.darkModeAutoSync.value &&
            !running &&
            hasOverlayPermission.value
        ) {
            val nightFlags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            if (nightFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                launchOverlays()
            }
        }

        // Keep the background shake monitor in sync with the current state.
        NightShieldController.syncShakeMonitor(applicationContext)

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
            NightShieldManager.allowShake.value,
        )
        OverlayHelpers.saveShakeIntensity(this, NightShieldManager.shakeIntensity.value)
        OverlayHelpers.saveGradualFade(this, NightShieldManager.gradualFadeEnabled.value && ProGate.isPro.value)
        OverlayHelpers.saveDimLevel(this, NightShieldManager.dimLevel.value)
        OverlayHelpers.saveAppTheme(this, NightShieldManager.appTheme.value)
        OverlayHelpers.saveWidgetStyle(this, NightShieldManager.widgetStyle.value)
        OverlayHelpers.saveSchedules(this, NightShieldManager.schedules.value)
        OverlayHelpers.saveAppConfigs(this, NightShieldManager.appFilterConfigs.value)
        OverlayHelpers.saveProfiles(this, NightShieldManager.profiles.value)
        OverlayHelpers.saveEyeBreakEnabled(this, NightShieldManager.eyeBreakEnabled.value)
        OverlayHelpers.saveDarkModeSync(this, NightShieldManager.darkModeAutoSync.value)
        AlarmHelpers.scheduleAll(this, NightShieldManager.schedules.value)
    }
}
