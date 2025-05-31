package com.vi5hnu.nightshield.widgets

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun DrawableIcon(@DrawableRes id: Int) {
    Icon(
        painter = painterResource(id = id),
        contentDescription = "icon",
        tint = MaterialTheme.colorScheme.onBackground, // Change color as needed
        modifier = Modifier.size(28.dp) // Adjust size if needed
    )
}