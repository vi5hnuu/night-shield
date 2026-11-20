package com.vi5hnu.nightshield.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.vi5hnu.nightshield.ui.theme.Radius

/**
 * A short "consider Pro" dialog: a title, one sentence, "See Pro features" and "Not now".
 *
 * Shared by every upgrade prompt (the one-time engagement prompt, and the note shown when an
 * interstitial ad can't load) so they look and behave the same. It only invites; the paywall
 * explains the details.
 *
 * @param onSeePro the user wants to see Pro; the caller opens the paywall.
 * @param onDismiss "Not now", back or a tap outside.
 */
@Composable
fun ProUpsellDialog(
    title: String,
    text: String,
    onSeePro: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Button(onClick = onSeePro, shape = RoundedCornerShape(Radius.sm)) {
                Text("See Pro features")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        },
    )
}
