package com.vi5hnu.nightshield.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vi5hnu.nightshield.ui.theme.Radius
import com.vi5hnu.nightshield.ui.theme.Spacing

/**
 * Label above a group of related rows.
 *
 * Small, uppercase-weighted and muted, so the eye reads it as a divider between groups rather than
 * as content — that distinction is what the old screen lacked when 14 same-sized card titles
 * competed with each other.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.xs, end = Spacing.xs, bottom = Spacing.sm),
    )
}

/**
 * Container for a group of [SettingRow]s.
 *
 * Dark UIs read elevation as a lighter fill rather than a shadow, so this uses a
 * `surfaceContainer` tone with no elevation instead of the shadowed cards used before.
 */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surfaceContainer,
        content = { Column(content = content) },
    )
}

/** Hairline separator between rows of the same [SettingsGroup]. */
@Composable
fun SettingsDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = Spacing.lg + 40.dp, end = Spacing.lg),
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 0.5.dp,
    )
}
