package dev.ignotus.openbuds.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.ignotus.openbuds.data.SonyHeadphoneUiState
import dev.ignotus.openbuds.ui.AppColorMode
import dev.ignotus.openbuds.ui.InfoLine
import dev.ignotus.openbuds.ui.ManagerCard
import dev.ignotus.openbuds.ui.PageColumn
import dev.ignotus.openbuds.ui.PageHeader
import dev.ignotus.openbuds.ui.ReareyeNavigationBarMode
import dev.ignotus.openbuds.ui.SectionCard
import dev.ignotus.openbuds.ui.SegmentedChoice
import dev.ignotus.openbuds.ui.SettingRow
import dev.ignotus.openbuds.ui.SettingsAnimatedRoute
import dev.ignotus.openbuds.ui.SettingsRoute
import dev.ignotus.openbuds.ui.StatusPill
import dev.ignotus.openbuds.ui.ThemeStyle
import dev.ignotus.openbuds.ui.UiRenderCapabilities
import dev.ignotus.openbuds.ui.reareyeHorizontalTransform
import dev.ignotus.openbuds.theme.OpenBudsSeedColors
import dev.ignotus.openbuds.lsposed.ProbeResultCache
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.ignotus.openbuds.R

@Composable
internal fun SettingsScreen(
    state: SonyHeadphoneUiState,
    bottomInnerPadding: Dp,
    renderCapabilities: UiRenderCapabilities,
    themeStyle: ThemeStyle,
    colorMode: AppColorMode,
    seedColorIndex: Int,
    routeStack: List<SettingsRoute>,
    quickTarget: SettingsRoute?,
    onQuickTargetConsumed: () -> Unit,
    onRouteStackChanged: (List<SettingsRoute>) -> Unit,
    onOverlayModeChanged: (Boolean) -> Unit,
    onNavigationBarModeChanged: (ReareyeNavigationBarMode) -> Unit,
    onEffectsEnabledChanged: (Boolean) -> Unit,
    onColorModeChanged: (AppColorMode) -> Unit,
    onSeedColorIndexChanged: (Int) -> Unit,
    onThemeStyleChanged: (ThemeStyle) -> Unit,
    onDebugLoggingChanged: (Boolean) -> Unit,
    onAutoReconnectChanged: (Boolean) -> Unit,
    onStrictScanFilterChanged: (Boolean) -> Unit,
    serviceBackgroundRun: Boolean,
    notificationPersistent: Boolean,
    connectionPopup: Boolean,
    hyperOsNotification: Boolean,
    controlCenterIntercept: Boolean,
    onServiceBackgroundRunChanged: (Boolean) -> Unit,
    onNotificationPersistentChanged: (Boolean) -> Unit,
    onConnectionPopupChanged: (Boolean) -> Unit,
    onHyperOsNotificationChanged: (Boolean) -> Unit,
    onControlCenterInterceptChanged: (Boolean) -> Unit,
) {
    val routeScope = rememberCoroutineScope()
    val currentRoute = routeStack.lastOrNull() ?: SettingsRoute.Root

    fun openRoute(route: SettingsRoute) {
        if (route == SettingsRoute.Root) return
        routeScope.launch {
            onOverlayModeChanged(true)
            delay(220L)
            onRouteStackChanged(listOf(SettingsRoute.Root, route))
        }
    }

    fun closeRoute() {
        routeScope.launch {
            onRouteStackChanged(listOf(SettingsRoute.Root))
            delay(220L)
            onOverlayModeChanged(false)
        }
    }

    LaunchedEffect(quickTarget) {
        quickTarget?.let { target ->
            onQuickTargetConsumed()
            openRoute(target)
        }
    }
    BackHandler(enabled = currentRoute != SettingsRoute.Root) {
        closeRoute()
    }

    AnimatedContent(
        targetState = SettingsAnimatedRoute(currentRoute, routeStack.size),
        contentKey = { it.route.name },
        transitionSpec = {
            reareyeHorizontalTransform(forward = targetState.depth >= initialState.depth)
        },
        label = "SettingsRouteTransition",
        modifier = Modifier.fillMaxSize(),
    ) { animatedRoute ->
        when (animatedRoute.route) {
            SettingsRoute.Root -> SettingsRootScreen(
                state = state,
                bottomInnerPadding = bottomInnerPadding,
                renderCapabilities = renderCapabilities,
                themeStyle = themeStyle,
                colorMode = colorMode,
                onOpenRoute = ::openRoute,
            )
            SettingsRoute.Appearance -> SettingsAppearanceScreen(
                bottomInnerPadding = bottomInnerPadding,
                renderCapabilities = renderCapabilities,
                themeStyle = themeStyle,
                colorMode = colorMode,
                seedColorIndex = seedColorIndex,
                onBack = ::closeRoute,
                onNavigationBarModeChanged = onNavigationBarModeChanged,
                onEffectsEnabledChanged = onEffectsEnabledChanged,
                onColorModeChanged = onColorModeChanged,
                onSeedColorIndexChanged = onSeedColorIndexChanged,
                onThemeStyleChanged = onThemeStyleChanged,
            )
            SettingsRoute.Protocol -> SettingsProtocolScreen(
                state = state,
                bottomInnerPadding = bottomInnerPadding,
                onBack = ::closeRoute,
                onAutoReconnectChanged = onAutoReconnectChanged,
                onStrictScanFilterChanged = onStrictScanFilterChanged,
            )
            SettingsRoute.Diagnostics -> SettingsDiagnosticsScreen(
                state = state,
                bottomInnerPadding = bottomInnerPadding,
                onBack = ::closeRoute,
                onDebugLoggingChanged = onDebugLoggingChanged,
            )
            SettingsRoute.Modules -> SettingsModulesScreen(
                bottomInnerPadding = bottomInnerPadding,
                onBack = ::closeRoute,
                serviceBackgroundRun = serviceBackgroundRun,
                notificationPersistent = notificationPersistent,
                connectionPopup = connectionPopup,
                hyperOsNotification = hyperOsNotification,
                controlCenterIntercept = controlCenterIntercept,
                onServiceBackgroundRunChanged = onServiceBackgroundRunChanged,
                onNotificationPersistentChanged = onNotificationPersistentChanged,
                onConnectionPopupChanged = onConnectionPopupChanged,
                onHyperOsNotificationChanged = onHyperOsNotificationChanged,
                onControlCenterInterceptChanged = onControlCenterInterceptChanged,
            )
        }
    }
}

@Composable
internal fun SettingsRootScreen(
    state: SonyHeadphoneUiState,
    bottomInnerPadding: Dp,
    renderCapabilities: UiRenderCapabilities,
    themeStyle: ThemeStyle,
    colorMode: AppColorMode,
    onOpenRoute: (SettingsRoute) -> Unit,
) {
    PageColumn(bottomInnerPadding = bottomInnerPadding) {
        PageHeader(
            title = stringResource(SettingsRoute.Root.titleResId),
            subtitle = stringResource(SettingsRoute.Root.subtitleResId),
        )
        ManagerCard(
            title = stringResource(SettingsRoute.Appearance.titleResId),
            summary = "${stringResource(renderCapabilities.mode.titleResId)} / ${
                when {
                    renderCapabilities.effectsEnabled -> "effects on"
                    else -> "effects off"
                }
                } / ${stringResource(colorMode.titleResId)} / ${stringResource(themeStyle.titleResId)}",
            icon = SettingsRoute.Appearance.icon,
            onClick = { onOpenRoute(SettingsRoute.Appearance) },
        )
        ManagerCard(
            title = stringResource(SettingsRoute.Protocol.titleResId),
            summary = "${state.preferredProtocol}; strict filter ${if (state.strictSonyScanFilter) stringResource(R.string.settings_enabled) else stringResource(R.string.settings_disabled)}",
            icon = SettingsRoute.Protocol.icon,
            onClick = { onOpenRoute(SettingsRoute.Protocol) },
        )
        ManagerCard(
            title = stringResource(SettingsRoute.Diagnostics.titleResId),
            summary = "${state.debugLogs.size} recent logs; debug ${if (state.debugLogging) stringResource(R.string.settings_enabled) else stringResource(R.string.settings_disabled)}",
            icon = SettingsRoute.Diagnostics.icon,
            onClick = { onOpenRoute(SettingsRoute.Diagnostics) },
        )
        ManagerCard(
            title = stringResource(SettingsRoute.Modules.titleResId),
            summary = stringResource(R.string.settings_modules_summary),
            icon = SettingsRoute.Modules.icon,
            onClick = { onOpenRoute(SettingsRoute.Modules) },
        )
    }
}

@Composable
internal fun SettingsAppearanceScreen(
    bottomInnerPadding: Dp,
    renderCapabilities: UiRenderCapabilities,
    themeStyle: ThemeStyle,
    colorMode: AppColorMode,
    seedColorIndex: Int,
    onBack: () -> Unit,
    onNavigationBarModeChanged: (ReareyeNavigationBarMode) -> Unit,
    onEffectsEnabledChanged: (Boolean) -> Unit,
    onColorModeChanged: (AppColorMode) -> Unit,
    onSeedColorIndexChanged: (Int) -> Unit,
    onThemeStyleChanged: (ThemeStyle) -> Unit,
) {
    PageColumn(bottomInnerPadding = bottomInnerPadding) {
        RouteHeader(route = SettingsRoute.Appearance, onBack = onBack)
        val ctx = LocalContext.current
        val navBarLabels = remember(ctx) { ReareyeNavigationBarMode.entries.associateWith { ctx.getString(it.titleResId) } }
        val colorModeLabels = remember(ctx) { AppColorMode.entries.associateWith { ctx.getString(it.titleResId) } }
        val themeStyleLabels = remember(ctx) { ThemeStyle.entries.associateWith { ctx.getString(it.titleResId) } }
        SectionCard(title = stringResource(R.string.settings_nav_surface), icon = Icons.Rounded.Settings) {
            SettingRow(
                title = stringResource(R.string.settings_bottom_bar_mode),
                subtitle = stringResource(R.string.settings_bottom_bar_mode_desc),
                trailing = {
                    SegmentedChoice(
                        selected = renderCapabilities.mode,
                        values = ReareyeNavigationBarMode.entries,
                        label = { navBarLabels[it] ?: it.name },
                        onSelected = onNavigationBarModeChanged,
                    )
                },
            )
            SettingRow(
                title = stringResource(R.string.settings_ui_effects),
                subtitle = stringResource(R.string.settings_ui_effects_desc),
                trailing = {
                    Switch(
                        checked = renderCapabilities.effectsEnabled,
                        onCheckedChange = onEffectsEnabledChanged,
                    )
                },
            )
            SettingRow(
                title = stringResource(R.string.settings_active_glass_path),
                subtitle = when {
                    !renderCapabilities.effectsEnabled -> stringResource(R.string.settings_glass_disabled)
                    renderCapabilities.liquidGlassEnabled -> stringResource(R.string.settings_glass_liquid)
                    renderCapabilities.semiTransparentBottomBar -> stringResource(R.string.settings_glass_semi)
                    renderCapabilities.floatingBottomBarEnabled -> stringResource(R.string.settings_glass_floating)
                    else -> stringResource(R.string.settings_glass_opaque)
                },
                trailing = {
                    StatusPill(
                        text = if (renderCapabilities.effectsEnabled) stringResource(R.string.settings_effects_live) else stringResource(R.string.settings_effects_off),
                        active = renderCapabilities.effectsEnabled,
                    )
                },
            )
        }
        SectionCard(title = stringResource(R.string.settings_theme_section)) {
            SettingRow(
                title = stringResource(R.string.settings_color_mode),
                subtitle = stringResource(R.string.settings_color_mode_desc),
                trailing = {
                    SegmentedChoice(
                        selected = colorMode,
                        values = AppColorMode.entries,
                        label = { colorModeLabels[it] ?: it.name },
                        onSelected = onColorModeChanged,
                    )
                },
            )
            SettingRow(
                title = stringResource(R.string.settings_theme_style),
                subtitle = stringResource(R.string.settings_theme_style_desc),
                trailing = {
                    SegmentedChoice(
                        selected = themeStyle,
                        values = ThemeStyle.entries,
                        label = { themeStyleLabels[it] ?: it.name },
                        onSelected = onThemeStyleChanged,
                    )
                },
            )
            SettingRow(
                title = stringResource(R.string.settings_seed_color),
                subtitle = stringResource(R.string.settings_seed_color_desc),
                trailing = {
                    SeedColorChoice(
                        selectedIndex = seedColorIndex,
                        onSelected = onSeedColorIndexChanged,
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeedColorChoice(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 4,
    ) {
        OpenBudsSeedColors.forEachIndexed { index, seed ->
            val selected = selectedIndex == index
            val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(seed.color)
                    .border(if (selected) 3.dp else 1.dp, borderColor, CircleShape)
                    .clickable { onSelected(index) },
            )
        }
    }
}

@Composable
internal fun SettingsProtocolScreen(
    state: SonyHeadphoneUiState,
    bottomInnerPadding: Dp,
    onBack: () -> Unit,
    onAutoReconnectChanged: (Boolean) -> Unit,
    onStrictScanFilterChanged: (Boolean) -> Unit,
) {
    PageColumn(bottomInnerPadding = bottomInnerPadding) {
        RouteHeader(route = SettingsRoute.Protocol, onBack = onBack)
        SectionCard(title = stringResource(R.string.settings_protocol_section)) {
            SettingRow(
                title = stringResource(R.string.settings_preferred_protocol),
                subtitle = state.preferredProtocol,
                trailing = { StatusPill(stringResource(R.string.settings_status_profile), true) },
            )
            SettingRow(
                title = stringResource(R.string.settings_strict_sony_filter),
                subtitle = stringResource(R.string.settings_strict_sony_filter_desc),
                trailing = {
                    Switch(
                        checked = state.strictSonyScanFilter,
                        onCheckedChange = onStrictScanFilterChanged,
                    )
                },
            )
            SettingRow(
                title = stringResource(R.string.settings_auto_reconnect),
                subtitle = stringResource(R.string.settings_auto_reconnect_desc),
                trailing = {
                    Switch(
                        checked = state.autoReconnect,
                        onCheckedChange = onAutoReconnectChanged,
                    )
                },
            )
        }
    }
}

@Composable
internal fun SettingsDiagnosticsScreen(
    state: SonyHeadphoneUiState,
    bottomInnerPadding: Dp,
    onBack: () -> Unit,
    onDebugLoggingChanged: (Boolean) -> Unit,
) {
    PageColumn(bottomInnerPadding = bottomInnerPadding) {
        RouteHeader(route = SettingsRoute.Diagnostics, onBack = onBack)
        SectionCard(title = stringResource(R.string.settings_diagnostics_section), icon = Icons.Rounded.Code) {
            SettingRow(
                title = stringResource(R.string.settings_debug_logging),
                subtitle = stringResource(R.string.settings_debug_logging_desc),
                trailing = {
                    Switch(
                        checked = state.debugLogging,
                        onCheckedChange = onDebugLoggingChanged,
                    )
                },
            )
            state.table2Diagnostic?.let { diagnostic ->
                Text(
                    text = stringResource(R.string.settings_last_table2),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp),
                )
                InfoLine(stringResource(R.string.settings_diag_channel), diagnostic.channel)
                InfoLine(stringResource(R.string.settings_diag_family), diagnostic.family)
                InfoLine(stringResource(R.string.settings_diag_command), diagnostic.command.hexByteOrUnknown())
                InfoLine(stringResource(R.string.settings_diag_inquired_type), diagnostic.inquiredType?.hexByteOrUnknown() ?: stringResource(R.string.home_unknown))
                InfoLine(stringResource(R.string.settings_diag_values), diagnostic.values.joinToString(prefix = "[", postfix = "]"))
                InfoLine(stringResource(R.string.settings_diag_raw), diagnostic.rawHex)
            }
            if (state.debugLogs.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_no_traffic),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            } else {
                state.debugLogs.take(12).forEach { log ->
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

private fun Int.hexByteOrUnknown(): String =
    if (this in 0..0xFF) "0x%02X".format(this) else "Unknown"

@Composable
internal fun SettingsModulesScreen(
    bottomInnerPadding: Dp,
    onBack: () -> Unit,
    serviceBackgroundRun: Boolean,
    notificationPersistent: Boolean,
    connectionPopup: Boolean,
    hyperOsNotification: Boolean,
    controlCenterIntercept: Boolean,
    onServiceBackgroundRunChanged: (Boolean) -> Unit,
    onNotificationPersistentChanged: (Boolean) -> Unit,
    onConnectionPopupChanged: (Boolean) -> Unit,
    onHyperOsNotificationChanged: (Boolean) -> Unit,
    onControlCenterInterceptChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var probeStatus by remember { mutableStateOf(context.getString(R.string.settings_loading)) }
    var lastProbe by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        ProbeResultCache.load(context)
        probeStatus = ProbeResultCache.isCompatible()
        lastProbe = ProbeResultCache.lastProbeTime()
    }

    PageColumn(bottomInnerPadding = bottomInnerPadding) {
        RouteHeader(route = SettingsRoute.Modules, onBack = onBack)

        SectionCard(title = stringResource(R.string.settings_bg_service)) {
            SettingRow(
                title = stringResource(R.string.settings_bg_service_title),
                subtitle = stringResource(R.string.settings_bg_service_desc),
                trailing = {
                    Switch(
                        checked = serviceBackgroundRun,
                        onCheckedChange = onServiceBackgroundRunChanged,
                    )
                },
            )
            SettingRow(
                title = stringResource(R.string.settings_persistent_notif),
                subtitle = stringResource(R.string.settings_persistent_notif_desc),
                trailing = {
                    Switch(
                        checked = notificationPersistent,
                        onCheckedChange = onNotificationPersistentChanged,
                    )
                },
            )
            SettingRow(
                title = stringResource(R.string.settings_connection_popup),
                subtitle = stringResource(R.string.settings_connection_popup_desc),
                trailing = {
                    Switch(
                        checked = connectionPopup,
                        onCheckedChange = onConnectionPopupChanged,
                    )
                },
            )
            val miuiNotifFound = ProbeResultCache.allResults()
                .any { it.className == "com.android.bluetooth.ble.app.MiuiBluetoothNotification" && it.found }
            if (miuiNotifFound) {
                SettingRow(
                    title = stringResource(R.string.settings_hyperos_notif),
                    subtitle = stringResource(R.string.settings_hyperos_notif_desc),
                    trailing = {
                        Switch(
                            checked = hyperOsNotification,
                            onCheckedChange = onHyperOsNotificationChanged,
                        )
                    },
                )
            }
            val ccCompat = ProbeResultCache.allResults()
                .any { it.className == "com.android.systemui.shared.plugins.PluginInstance" && it.found }
            if (ccCompat) {
                SettingRow(
                    title = stringResource(R.string.settings_control_center_intercept),
                    subtitle = stringResource(R.string.settings_control_center_intercept_desc),
                    trailing = {
                        Switch(
                            checked = controlCenterIntercept,
                            onCheckedChange = onControlCenterInterceptChanged,
                        )
                    },
                )
            }
        }

        SectionCard(title = stringResource(R.string.settings_lsposed_integration)) {
            SettingRow(
                title = stringResource(R.string.settings_rom_compat),
                subtitle = probeStatus,
                trailing = { StatusPill(probeStatus, probeStatus == stringResource(R.string.settings_status_compatible)) },
            )
            if (lastProbe > 0) {
                SettingRow(
                    title = stringResource(R.string.settings_last_probe),
                    subtitle = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date(lastProbe)),
                    trailing = { StatusPill(stringResource(R.string.settings_status_probed), true) },
                )
            }
            SettingRow(
                title = stringResource(R.string.settings_experimental_feature),
                subtitle = stringResource(R.string.settings_experimental_feature_desc),
                trailing = { StatusPill(stringResource(R.string.settings_status_caution), false) },
            )
        }
    }
}

@Composable
internal fun RouteHeader(route: SettingsRoute, onBack: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.nav_back),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(route.titleResId),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(route.subtitleResId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
