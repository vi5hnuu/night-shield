package com.vi5hnu.nightshield

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.*
import com.vi5hnu.nightshield.ui.theme.NightShieldTheme
import com.vi5hnu.nightshield.screens.HomeScreen
import androidx.core.net.toUri
import java.util.Calendar


class MainActivity : ComponentActivity() {
    private var hasOverlayPermissionState = mutableStateOf(false)
    private var areServicesRunning = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hasOverlayPermissionState.value = OverlayHelpers.checkOverlayPermission(applicationContext);
        areServicesRunning.value = OverlayHelpers.areOverlaysActive(applicationContext);
        launchOverlays();
        setContent {
            Log.d("Recomposition", "Main activity recomposing")
            LaunchedEffect(areServicesRunning.value) {
                NightShieldWidgetProvider.updateWidget(applicationContext);
            }

            NightShieldTheme(dynamicColor = false) {
                HomeScreen(
                    onAllowShake={ NightShieldManager.setAllowShake(it)},
                    allowShake= NightShieldManager.allowShake.collectAsState().value,
                    areServicesActive = areServicesRunning.value,
                    hasOverlayPermission = hasOverlayPermissionState.value,
                    onPermissionRequest = {requestOverlayPermission()},
                    launchOverlays = {launchOverlays()},
                    stopOverlays = {stopOverlays()})
            }
        }
    }

    private fun stopOverlays(){
        stopOverlayService();
        OverlayHelpers.setOverlaysActive(this, false);
        areServicesRunning.value = false
    }

    private fun launchOverlays(){
        if (!hasOverlayPermissionState.value) return;
        startOverlayService();
        OverlayHelpers.setOverlaysActive(this, true);
        areServicesRunning.value = true
    }

    private fun requestOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        intent.data = "package:$packageName".toUri()
        startActivity(intent);
    }

    private fun startOverlayService() {
        Intent(this, NightShieldService::class.java).also {startForegroundService(it)}
    }

    private fun stopOverlayService() {
        Intent(this, NightShieldService::class.java).also { stopService(it) }
    }


    override fun onResume() {
        super.onResume()
        hasOverlayPermissionState.value = OverlayHelpers.checkOverlayPermission(applicationContext);
        areServicesRunning.value= OverlayHelpers.areOverlaysActive(applicationContext);
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

