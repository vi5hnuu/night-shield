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
fun DrawableIcon(@DrawableRes id: Int, contentDescription: String = "") {
    Icon(
        painter = painterResource(id = id),
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(24.dp)
    )
}
