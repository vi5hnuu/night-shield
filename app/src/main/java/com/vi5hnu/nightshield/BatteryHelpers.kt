package com.vi5hnu.nightshield

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Helpers for the two settings users must adjust so background shake (a foreground service)
 * survives aggressive OEM battery management.
 *
 * 1. [requestIgnoreBatteryOptimizations] — the standard Android exemption dialog.
 * 2. [openAutoStartSettings] — the OEM-specific "auto-start / background activity" screen, which
 *    on Xiaomi/Oppo/Vivo/Huawei etc. is a *separate* killer from battery optimisation. We try the
 *    known component for each OEM and fall back to the app-details screen if none resolves.
 */
object BatteryHelpers {

    fun requestIgnoreBatteryOptimizations(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.onFailure { openAppDetails(context) }
    }

    /** Opens the OEM auto-start manager, or the app-details screen as a universal fallback. */
    fun openAutoStartSettings(context: Context) {
        for (component in OEM_AUTOSTART_COMPONENTS) {
            val intent = Intent().apply {
                this.component = component
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // resolveActivity → only launch if the component actually exists on this device.
            if (intent.resolveActivity(context.packageManager) != null) {
                if (runCatching { context.startActivity(intent) }.isSuccess) return
            }
        }
        openAppDetails(context)
    }

    private fun openAppDetails(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    // Known OEM auto-start / background-launch managers, tried in order.
    private val OEM_AUTOSTART_COMPONENTS = listOf(
        // Xiaomi / Redmi / POCO (MIUI)
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        // Oppo / Realme (ColorOS)
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        // Vivo (FuntouchOS)
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        // Huawei / Honor (EMUI)
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        // OnePlus (OxygenOS)
        ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
        // Samsung (Device Care)
        ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
    )
}
