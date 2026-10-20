package com.vi5hnu.nightshield

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.gms.ads.MobileAds
import com.vi5hnu.nightshield.screens.NightShieldActions
import com.vi5hnu.nightshield.screens.NightShieldApp
import com.vi5hnu.nightshield.screens.OnboardingScreen
import com.vi5hnu.nightshield.ui.theme.NightShieldTheme

class MainActivity : ComponentActivity() {

    private var hasOverlayPermission = mutableStateOf(false)

    /**
     * Set when the activity is opened to show the paywall (a locked widget control). Consumed by
     * the UI once shown, so a rotation or resume does not reopen it.
     */
    private val upgradeRequested = mutableStateOf(false)

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

    // ── Auto sunset/sunrise: location permission + fetch ──────────────────────
    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) fetchLocationAndEnable()
        else Toast.makeText(this, "Location permission is needed for auto sunset/sunrise", Toast.LENGTH_LONG).show()
    }

    private fun enableAutoSchedule() {
        if (hasLocationPermission()) fetchLocationAndEnable()
        else requestLocationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun disableAutoSchedule() {
        NightShieldManager.setAutoScheduleEnabled(false)
        OverlayHelpers.saveAutoScheduleEnabled(this, false)
        AutoScheduleHelper.reschedule(this)
    }

    private fun refreshLocation() {
        if (!hasLocationPermission()) {
            requestLocationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            return
        }
        Toast.makeText(this, "Refreshing location…", Toast.LENGTH_SHORT).show()
        LocationHelper.fetchAndCache(this) { city ->
            if (city != null) {
                AutoScheduleHelper.reschedule(this)
                Toast.makeText(this, "Location: $city", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(this, "Couldn't get location. Try again outdoors.", Toast.LENGTH_LONG).show()
        }
    }

    private fun fetchLocationAndEnable() {
        Toast.makeText(this, "Getting your location…", Toast.LENGTH_SHORT).show()
        LocationHelper.fetchAndCache(this) { city ->
            if (city != null) {
                NightShieldManager.setAutoScheduleEnabled(true)
                OverlayHelpers.saveAutoScheduleEnabled(this, true)
                AutoScheduleHelper.reschedule(this, activateIfNight = true)
                Toast.makeText(this, "Auto schedule on · $city", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(this, "Couldn't get location. Try again outdoors.", Toast.LENGTH_LONG).show()
        }
    }

    // ── Notifications (Android 13+) ───────────────────────────────────────────
    // The permission was never declared or requested, so on API 33+ the filter's notification
    // (with the Pro ±10% controls), eye-break reminders and the weekly report were all invisible.
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // The filter's foreground notification was posted (and dropped) before the user answered.
        // Re-delivering the start command makes the running service call startForeground again,
        // which re-posts it; the service treats a repeat start as a no-op otherwise.
        if (granted && NightShieldService.isRunning) startOverlayService()
    }

    /**
     * Asks once per contextual trigger. Android itself stops showing the dialog after the user
     * denies twice, so calling this from several places cannot turn into nagging.
     */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeUpgradeRequest(intent)

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
        NightShieldManager.setBackgroundShake(OverlayHelpers.loadBackgroundShake(applicationContext))
        NightShieldManager.setShakeIntensity(OverlayHelpers.loadShakeIntensity(applicationContext))
        NightShieldManager.setGradualFadeEnabled(OverlayHelpers.loadGradualFade(applicationContext) && ProGate.isPro.value)
        NightShieldManager.setDimLevel(OverlayHelpers.loadDimLevel(applicationContext))
        NightShieldManager.setAdaptiveIntensity(OverlayHelpers.loadAdaptiveIntensity(applicationContext) && ProGate.isPro.value)
        NightShieldManager.setAutoScheduleEnabled(OverlayHelpers.loadAutoScheduleEnabled(applicationContext) && ProGate.isPro.value)
        NightShieldManager.setAutoCity(OverlayHelpers.loadAutoLocation(applicationContext)?.city ?: "")
        AutoScheduleHelper.reschedule(applicationContext)
        // Gated on load like the Pro toggles above: a stored Pro theme or widget style must not
        // apply without entitlement (restored backup, or a refund processed on another device).
        NightShieldManager.setAppTheme(
            OverlayHelpers.loadAppTheme(applicationContext).gatedBy(ProGate.isPro.value)
        )
        NightShieldManager.setWidgetStyle(
            OverlayHelpers.loadWidgetStyle(applicationContext).gatedBy(ProGate.isPro.value)
        )

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

            // Review breakpoint: a completed purchase (isPro flipping on while the UI is up).
            // The other value moments are reported through NightShieldActions.onReviewMoment.
            var wasPro by remember { mutableStateOf(isPro) }
            LaunchedEffect(isPro) {
                if (isPro && !wasPro) {
                    ReviewPrompter.onMoment(this@MainActivity, ReviewPrompter.Moment.PURCHASED)
                }
                wasPro = isPro
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
                    // Built once and passed down: every callback below needs this Activity or one
                    // of its result launchers, so none of them can live inside the screens.
                    val actions = remember {
                        NightShieldActions(
                            onAllowShake = {
                                NightShieldManager.setAllowShake(it)
                                OverlayHelpers.saveFilterSettings(
                                    applicationContext,
                                    NightShieldManager.canvasColor.value,
                                    NightShieldManager.filterIntensity.value,
                                    it,
                                )
                                // Shake toggle changed — start or stop the background monitor + in-app detector.
                                NightShieldController.syncShakeMonitor(applicationContext)
                                syncInAppShake()
                            },
                            onPermissionRequest = { requestOverlayPermission() },
                            launchOverlays = { launchOverlays() },
                            stopOverlays = { stopOverlays() },
                            onUpgradePromptShown = { OverlayHelpers.markUpgradePromptShown(applicationContext) },
                            onPurchase = { BillingManager.purchase(this@MainActivity) },
                            onRestorePurchase = { BillingManager.restore(this@MainActivity) },
                            onExportSettings = { createDocumentLauncher.launch("nightshield_backup.json") },
                            onImportSettings = { openDocumentLauncher.launch(arrayOf("application/json")) },
                            onEnableAutoSchedule = { enableAutoSchedule() },
                            onDisableAutoSchedule = { disableAutoSchedule() },
                            onRefreshLocation = { refreshLocation() },
                            ensureNotificationPermission = { ensureNotificationPermission() },
                            onReviewMoment = { ReviewPrompter.onMoment(this@MainActivity, it) },
                        )
                    }

                    NightShieldApp(
                        hasOverlayPermission = hasOverlayPermission.value,
                        areServicesActive    = areServicesActive,
                        allowShake           = NightShieldManager.allowShake.collectAsState().value,
                        isPro                = isPro,
                        triggerUpgradePrompt = triggerUpgrade,
                        upgradeRequested     = upgradeRequested.value,
                        onUpgradeRequestHandled = { upgradeRequested.value = false },
                        actions              = actions,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeUpgradeRequest(intent)
    }

    private fun consumeUpgradeRequest(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_SHOW_UPGRADE, false) == true) {
            upgradeRequested.value = true
            intent.removeExtra(EXTRA_SHOW_UPGRADE)
        }
    }

    private fun launchOverlays() {
        // The service flips the durable flag + widget; optimistically mirror the flow for
        // instant button feedback. The service confirms (or self-corrects if it can't start).
        if (!NightShieldController.activate(this)) return
        NightShieldManager.setFilterActive(true)
        // The filter's notification carries the off switch (and Pro intensity controls).
        ensureNotificationPermission()
        ReviewPrompter.onMoment(this, ReviewPrompter.Moment.FILTER_ON)
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

        // PRO — follow system dark mode.
        if (!running && DarkModeSync.maybeActivate(this, resources.configuration)) {
            NightShieldManager.setFilterActive(true)
        }

        // Re-verify entitlement with Play on every foreground. Without this, both Pro status and
        // the store price are frozen at whatever the process's first connection attempt returned:
        // a user who launched while offline would stay locked out of what they bought, and a
        // refund would go unnoticed for as long as the process lives.
        BillingManager.refresh()

        // Keep the background shake monitor in sync with the current state.
        NightShieldController.syncShakeMonitor(applicationContext)
        // In-app shake: works even in battery-saver mode (background monitor off) since the
        // in-app composable detector was removed. Deduped with the service/monitor via tryShakeToggle.
        syncInAppShake()

        // Don't show an app-open ad on the FIRST resume (the cold/intentional open) — that just
        // blacks out the screen the user is trying to reach. Only show on later returns to the app.
        if (hasResumedOnce) AppOpenAdManager.showAdIfAvailable(this)
        hasResumedOnce = true
    }

    private var hasResumedOnce = false

    override fun onPause() {
        super.onPause()
        inAppShake?.stop()
        inAppShake = null
        persistSettings()
    }

    // ── In-app shake (active only while the activity is resumed) ──────────────
    private var inAppShake: ShakeHelper? = null

    private fun syncInAppShake() {
        if (NightShieldManager.allowShake.value) {
            if (inAppShake == null) {
                inAppShake = ShakeHelper(this) {
                    NightShieldManager.tryShakeToggle {
                        ShakeHelper.hapticFeedback(this)
                        NightShieldController.toggle(this)   // service updates flag/flow/widget
                    }
                }.also { it.start() }
            }
        } else {
            inAppShake?.stop()
            inAppShake = null
        }
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
        OverlayHelpers.saveAdaptiveIntensity(this, NightShieldManager.adaptiveIntensity.value && ProGate.isPro.value)
        OverlayHelpers.saveAppTheme(this, NightShieldManager.appTheme.value)
        OverlayHelpers.saveWidgetStyle(this, NightShieldManager.widgetStyle.value)
        OverlayHelpers.saveAppConfigs(this, NightShieldManager.appFilterConfigs.value)
        OverlayHelpers.saveProfiles(this, NightShieldManager.profiles.value)
        OverlayHelpers.saveEyeBreakEnabled(this, NightShieldManager.eyeBreakEnabled.value)
        OverlayHelpers.saveDarkModeSync(this, NightShieldManager.darkModeAutoSync.value)
        // Schedules were previously armed uncapped here, so a refunded user kept every alarm.
        AlarmHelpers.commit(this, NightShieldManager.schedules.value)
    }

    companion object {
        /** Boolean extra: open straight to the Pro upgrade screen. */
        const val EXTRA_SHOW_UPGRADE = "show_upgrade"
    }
}
