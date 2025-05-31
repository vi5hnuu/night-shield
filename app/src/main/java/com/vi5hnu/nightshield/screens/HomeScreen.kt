package com.vi5hnu.nightshield.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vi5hnu.nightshield.R
import androidx.compose.runtime.*
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import com.vi5hnu.nightshield.NightShieldManager
import com.vi5hnu.nightshield.widgets.ColorDot
import com.vi5hnu.nightshield.widgets.ShakeDetector
import com.vi5hnu.nightshield.widgets.ColorPicker
import com.vi5hnu.nightshield.widgets.Tile

@Composable
fun HomeScreen(hasOverlayPermission: Boolean,
               areServicesActive: Boolean,
               launchOverlays:()-> Unit,
               stopOverlays:()-> Unit,
               allowShake: Boolean=true,
               onAllowShake:(allow: Boolean)-> Unit,
               onPermissionRequest: () -> Unit) {
    if(NightShieldManager.shakeActive.collectAsState().value) ShakeDetector { if(areServicesActive) stopOverlays() else if(hasOverlayPermission) launchOverlays() else onPermissionRequest() }
    Surface(modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(3.0f)
                    .paint(
                        painter = painterResource(id = R.drawable.cover), // Replace with your WebP resource
                        contentScale = ContentScale.Crop
                    )
            ) {
                Button(
                    modifier = Modifier.padding(all = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha=0.2f)), // Set button background color
                    onClick = { if(hasOverlayPermission) (if(areServicesActive) stopOverlays() else launchOverlays()) else onPermissionRequest() }
                ) {
                    Text(
                        if(hasOverlayPermission) (if(areServicesActive)  "Stop" else "Activate") else "Request Permission",
                        color = MaterialTheme.colorScheme.onPrimary
                    ) // Ensure readable text color
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(7.0f)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Tile(
                        id = R.drawable.vibration_24px,
                        title = "Disable Shake",
                        subtitle = "You cannot toggle device control"){
                        Switch(checked = allowShake, onCheckedChange = {onAllowShake(it)})
                    }
                    Tile(
                        id = R.drawable.format_paint_24px,
                        title = "Filter color",
                        subtitle = "Choose a soothing color for your eyes"){
                        val canvasColor=NightShieldManager.canvasColor.collectAsState()
                        ColorDot(color = canvasColor.value) {
                            NightShieldManager.setIsCanvasColorPickerVisible(true);
                        }
                    }
                }
            }
            Box(modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)){
                Text("Made with 🥰 by Vishnu Kumar", modifier = Modifier.align(Alignment.Center).padding(bottom = 16.dp), color = Color.White)
            }
        }
    }
}



