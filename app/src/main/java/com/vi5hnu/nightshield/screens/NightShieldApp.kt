package com.vi5hnu.nightshield.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.vi5hnu.nightshield.R
import com.vi5hnu.nightshield.screens.apps.AppsScreen
import com.vi5hnu.nightshield.screens.filter.FilterScreen
import com.vi5hnu.nightshield.screens.schedule.ScheduleScreen
import com.vi5hnu.nightshield.screens.settings.SettingsScreen
import com.vi5hnu.nightshield.ui.components.ProBadge
import com.vi5hnu.nightshield.ui.theme.IconSize
import com.vi5hnu.nightshield.ui.theme.Radius

/**
 * The app's four top-level destinations.
 *
 * A plain enum plus [AnimatedContent] is enough here: the destinations are fixed, take no
 * arguments, and have no deep links, so a navigation graph would add a dependency and indirection
 * without buying anything. Anything deeper than a tab (upgrade, pickers) is a dialog, a bottom
 * sheet, or the full-screen overlay handled below.
 */
private enum class NightShieldTab(
    val label: String,
    @param:DrawableRes val icon: Int,
) {
    FILTER("Filter", R.drawable.ic_moon_24),
    SCHEDULE("Schedule", R.drawable.ic_schedule_24),
    APPS("Apps", R.drawable.ic_apps_24),
    SETTINGS("Settings", R.drawable.ic_settings_24),
}

/**
 * Root of the app UI: top bar, tab content and bottom navigation.
 *
 * Replaces the single scrolling screen that held all fourteen feature sections; each tab now owns
 * one coherent group so no screen is longer than a few scrolls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightShieldApp(
    hasOverlayPermission: Boolean,
    areServicesActive: Boolean,
    allowShake: Boolean,
    isPro: Boolean,
    triggerUpgradePrompt: Boolean,
    actions: NightShieldActions,
) {
    var currentTab by remember { mutableStateOf(NightShieldTab.FILTER) }
    var showUpgradeScreen by remember { mutableStateOf(false) }
    var showUpgradeDialog by remember { mutableStateOf(triggerUpgradePrompt) }

    // Contextual upgrade prompt — fires once after sufficient engagement.
    if (showUpgradeDialog) {
        AlertDialog(
            onDismissRequest = { showUpgradeDialog = false; actions.onUpgradePromptShown() },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Enjoying Night Shield?", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    "Unlock Smart Profiles, Blue Light Report, Gradual Fade-in and 8 more Pro " +
                        "features — one payment, no subscription, forever yours.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUpgradeDialog = false
                        actions.onUpgradePromptShown()
                        showUpgradeScreen = true
                    },
                    shape = RoundedCornerShape(Radius.sm),
                ) { Text("See Pro features") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUpgradeDialog = false; actions.onUpgradePromptShown() },
                ) { Text("Not now") }
            },
        )
    }

    if (showUpgradeScreen) {
        // The upgrade screen is a state-driven overlay (no Jetpack Nav back stack), so system back
        // must be intercepted explicitly — otherwise it leaves the activity. Mandatory from
        // targetSdk 36: predictive back is on by default there, meaning onBackPressed() is never
        // called and KEYCODE_BACK is not dispatched; BackHandler is the supported API and also
        // drives the correct "return to home screen" predictive preview instead of app exit.
        BackHandler { showUpgradeScreen = false }
        UpgradeScreen(
            isPro = isPro,
            onPurchase = actions.onPurchase,
            onRestorePurchase = actions.onRestorePurchase,
            onDismiss = { showUpgradeScreen = false },
        )
        return
    }

    // Back from a secondary tab returns to Filter rather than leaving the app, matching how
    // bottom-navigation apps are expected to behave.
    BackHandler(enabled = currentTab != NightShieldTab.FILTER) {
        currentTab = NightShieldTab.FILTER
    }

    val showUpgrade = { showUpgradeScreen = true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (currentTab == NightShieldTab.FILTER) {
                            stringResource(R.string.app_name)
                        } else {
                            currentTab.label
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    // One entry point to the paywall, always in the same place.
                    if (!isPro) {
                        ProBadge(onClick = showUpgrade, modifier = Modifier.padding(end = 12.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            Column {
                // Banner ad (free users only) sits above the nav bar so it never covers content
                // or the navigation itself.
                if (!isPro) BannerAd()
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    NightShieldTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    painter = painterResource(tab.icon),
                                    contentDescription = tab.label,
                                    modifier = Modifier.size(IconSize.md),
                                )
                            },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background,
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                label = "tab",
            ) { tab ->
                when (tab) {
                    NightShieldTab.FILTER -> FilterScreen(
                        hasOverlayPermission = hasOverlayPermission,
                        areServicesActive = areServicesActive,
                        allowShake = allowShake,
                        isPro = isPro,
                        actions = actions,
                        onShowUpgrade = showUpgrade,
                    )

                    NightShieldTab.SCHEDULE -> ScheduleScreen(
                        isPro = isPro,
                        actions = actions,
                        onShowUpgrade = showUpgrade,
                    )

                    NightShieldTab.APPS -> AppsScreen(
                        isPro = isPro,
                        onShowUpgrade = showUpgrade,
                    )

                    NightShieldTab.SETTINGS -> SettingsScreen(
                        isPro = isPro,
                        actions = actions,
                        onShowUpgrade = showUpgrade,
                    )
                }
            }
        }
    }
}

// ── Banner ad ─────────────────────────────────────────────────────────────────

private const val BANNER_AD_UNIT_ID = "ca-app-pub-4715945578201106/5606751408"

@Composable
private fun BannerAd() {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BANNER_AD_UNIT_ID
                loadAd(AdRequest.Builder().build())
            }
        },
    )
}
