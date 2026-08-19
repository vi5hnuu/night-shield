package com.vi5hnu.nightshield.screens.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.vi5hnu.nightshield.AppFilterConfig
import com.vi5hnu.nightshield.NightShieldManager
import com.vi5hnu.nightshield.R
import com.vi5hnu.nightshield.ui.components.EmptyState
import com.vi5hnu.nightshield.ui.components.InfoBanner
import com.vi5hnu.nightshield.ui.components.InfoBannerAction
import com.vi5hnu.nightshield.ui.components.ProBadge
import com.vi5hnu.nightshield.ui.components.SectionHeader
import com.vi5hnu.nightshield.ui.components.SettingsDivider
import com.vi5hnu.nightshield.ui.components.SettingsGroup
import com.vi5hnu.nightshield.ui.theme.IconSize
import com.vi5hnu.nightshield.ui.theme.Radius
import com.vi5hnu.nightshield.ui.theme.Spacing
import com.vi5hnu.nightshield.widgets.ColorDot
import com.vi5hnu.nightshield.widgets.ColorPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Per-app filter behaviour: which apps pause the filter, and (with Pro) which get their own
 * intensity and colour.
 */
@Composable
fun AppsScreen(
    isPro: Boolean,
    onShowUpgrade: () -> Unit,
) {
    val context = LocalContext.current
    val configs by NightShieldManager.appFilterConfigs.collectAsState()
    var showAppPicker by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    // Free users are limited to three per-app rules; Pro is unlimited.
    val canAddMore = isPro || configs.size < 3

    // Accessibility status is re-read on each lifecycle change, so the banner clears as soon as
    // the user returns from enabling the service.
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val accessibilityEnabled = remember(lifecycleState) {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: ""
        enabledServices.contains(context.packageName, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
    ) {
        Spacer(Modifier.height(Spacing.sm))

        if (!accessibilityEnabled) {
            InfoBanner(
                title = "Accessibility service is off",
                text = "Night Shield needs it to see which app is in the foreground before per-app rules can apply.",
                icon = R.drawable.ic_apps_24,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
                actions = listOf(
                    InfoBannerAction("Enable", emphasised = true) { showAccessibilityDialog = true },
                ),
            )
            Spacer(Modifier.height(Spacing.lg))
        }

        SectionHeader(stringResource(R.string.per_app_filter_title))

        SettingsGroup {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.lg, end = Spacing.md, top = Spacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.per_app_filter_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (canAddMore) {
                    FilledTonalIconButton(
                        onClick = { showAppPicker = true },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Icon(painterResource(R.drawable.ic_add_24), contentDescription = "Add app")
                    }
                } else {
                    ProBadge(onClick = onShowUpgrade)
                }
            }

            if (configs.isEmpty()) {
                EmptyState(
                    icon = R.drawable.ic_apps_24,
                    title = "No apps configured",
                    description = "Add an app to pause or customise the filter while it is open.",
                )
            } else {
                Spacer(Modifier.height(Spacing.sm))
                configs.values.forEachIndexed { index, config ->
                    AppConfigRow(
                        config = config,
                        isPro = isPro,
                        onShowUpgrade = onShowUpgrade,
                        onUpdate = { NightShieldManager.setAppFilterConfig(it) },
                        onDelete = { NightShieldManager.removeAppFilterConfig(config.packageName) },
                    )
                    if (index < configs.size - 1) SettingsDivider()
                }
                Spacer(Modifier.height(Spacing.sm))
            }
        }

        Spacer(Modifier.height(Spacing.xxxl))
    }

    if (showAccessibilityDialog) {
        AccessibilityDialog(
            onDismiss = { showAccessibilityDialog = false },
            onAllow = {
                showAccessibilityDialog = false
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
        )
    }

    if (showAppPicker) {
        AppPickerSheet(
            context = context,
            alreadyAdded = configs.keys,
            onDismiss = { showAppPicker = false },
            onAppSelected = { pkg, label ->
                NightShieldManager.setAppFilterConfig(
                    AppFilterConfig(packageName = pkg, appLabel = label),
                )
                showAppPicker = false
            },
        )
    }
}

@Composable
private fun AccessibilityDialog(onDismiss: () -> Unit, onAllow: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Allow accessibility access", style = MaterialTheme.typography.titleMedium) },
        text = {
            Text(
                "Night Shield uses the Accessibility API only to detect which app is in the " +
                    "foreground, so it can pause or adjust the filter per app.\n\n" +
                    "It does not read screen content, text, passwords or personal data.\n\n" +
                    "\"Allow\" opens Android's Accessibility settings, where you can enable " +
                    "Night Shield under Installed services.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = { Button(onClick = onAllow) { Text("Allow") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("No thanks") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppConfigRow(
    config: AppFilterConfig,
    isPro: Boolean,
    onShowUpgrade: () -> Unit,
    onUpdate: (AppFilterConfig) -> Unit,
    onDelete: () -> Unit,
) {
    var showColorSheet by remember { mutableStateOf(false) }
    val globalIntensity by NightShieldManager.filterIntensity.collectAsState()
    val globalColor by NightShieldManager.canvasColor.collectAsState()

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            AppIcon(packageName = config.packageName, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    config.appLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (config.filterDisabled) "Filter paused in this app" else "Filter active in this app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = config.filterDisabled,
                onCheckedChange = { onUpdate(config.copy(filterDisabled = it)) },
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(
                    painterResource(R.drawable.ic_delete_24),
                    contentDescription = "Remove ${config.appLabel}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.sm + 2.dp),
                )
            }
        }

        // Custom intensity and colour apply only while the filter runs in that app.
        AnimatedVisibility(visible = !config.filterDisabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.lg + 52.dp, end = Spacing.lg, top = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (isPro) {
                    Text(
                        "Intensity",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = config.customIntensity ?: globalIntensity,
                        onValueChange = { onUpdate(config.copy(customIntensity = it)) },
                        valueRange = 0.1f..1.0f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                    )
                    ColorDot(color = config.customColor ?: globalColor, sizeDp = 28.dp) {
                        showColorSheet = true
                    }
                } else {
                    Text(
                        "Custom intensity & colour",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    ProBadge(onClick = onShowUpgrade)
                }
            }
        }
    }

    if (showColorSheet && isPro) {
        ModalBottomSheet(
            onDismissRequest = { showColorSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            dragHandle = null,
        ) {
            ColorPicker(
                initialColor = config.customColor ?: globalColor,
                onChange = { onUpdate(config.copy(customColor = it)) },
                onDismiss = { showColorSheet = false },
            )
        }
    }
}

/** App launcher icon, loaded off the main thread and cached per package by [produceState]. */
@Composable
private fun AppIcon(packageName: String, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager
                    .getApplicationIcon(packageName)
                    .toBitmap(96, 96)
                    .asImageBitmap()
            }.getOrNull()
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(Radius.sm)),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    RoundedCornerShape(Radius.sm),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_apps_24),
                contentDescription = null,
                modifier = Modifier.size(IconSize.md - 4.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Installed-app picker ──────────────────────────────────────────────────────

private data class AppInfo(val packageName: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSheet(
    context: Context,
    alreadyAdded: Set<String>,
    onDismiss: () -> Unit,
    onAppSelected: (packageName: String, label: String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            // queryIntentActivities with ACTION_MAIN + CATEGORY_LAUNCHER returns every app that
            // has a launcher icon, including system apps that getInstalledApplications(0) drops on
            // Android 11+. The <queries> block in the manifest makes this work without
            // QUERY_ALL_PACKAGES.
            val launchIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            pm.queryIntentActivities(launchIntent, PackageManager.MATCH_ALL)
                .map { it.activityInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != context.packageName }
                .map { AppInfo(it.packageName, it.loadLabel(pm).toString()) }
                .sortedBy { it.label.lowercase() }
        }
        allApps = apps
        isLoading = false
    }

    val filtered = remember(query, allApps, alreadyAdded) {
        allApps.filter { app ->
            app.packageName !in alreadyAdded &&
                (
                    query.isBlank() ||
                        app.label.contains(query, ignoreCase = true) ||
                        app.packageName.contains(query, ignoreCase = true)
                    )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.85f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Choose an app", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss) {
                    Icon(
                        painterResource(R.drawable.ic_close_24),
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize.md - 4.dp),
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps", style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                shape = RoundedCornerShape(Radius.md),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )

            Spacer(Modifier.height(Spacing.sm))

            when {
                isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.xxxl),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }

                filtered.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.xxl),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (query.isBlank()) "No apps found" else "No apps match \"$query\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(filtered, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAppSelected(app.packageName, app.label) }
                                .padding(horizontal = Spacing.xl, vertical = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            AppIcon(packageName = app.packageName, size = 40.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    app.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = Spacing.xl),
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 0.5.dp,
                        )
                    }
                    item { Spacer(Modifier.navigationBarsPadding().height(Spacing.lg)) }
                }
            }
        }
    }
}
