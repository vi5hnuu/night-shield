package com.vi5hnu.nightshield.screens

import androidx.compose.runtime.Immutable

/**
 * Everything the UI can ask the host activity to do.
 *
 * These callbacks all need an `Activity` (permission dialogs, billing flows, document pickers) or
 * activity-scoped launchers, so they cannot live in the screens themselves. Bundling them keeps the
 * four tab screens from each declaring their own slice of the same parameter list — the old single
 * screen took seventeen individual lambdas.
 */
@Immutable
data class NightShieldActions(
    val onAllowShake: (Boolean) -> Unit,
    val onPermissionRequest: () -> Unit,
    val launchOverlays: () -> Unit,
    val stopOverlays: () -> Unit,
    val onUpgradePromptShown: () -> Unit = {},
    val onPurchase: () -> Unit = {},
    val onRestorePurchase: () -> Unit = {},
    val onExportSettings: () -> Unit = {},
    val onImportSettings: () -> Unit = {},
    val onEnableAutoSchedule: () -> Unit = {},
    val onDisableAutoSchedule: () -> Unit = {},
    val onRefreshLocation: () -> Unit = {},
)
