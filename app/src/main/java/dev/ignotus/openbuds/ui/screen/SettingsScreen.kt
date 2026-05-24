package dev.ignotus.openbuds.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun SettingsScreen(
    state: SonyHeadphoneUiState,
    bottomInnerPadding: Dp,
    renderCapabilities: UiRenderCapabilities,
    themeStyle: ThemeStyle,
    colorMode: AppColorMode,
    routeStack: List<SettingsRoute>,
    quickTarget: SettingsRoute?,
    onQuickTargetConsumed: () -> Unit,
    onRouteStackChanged: (List<SettingsRoute>) -> Unit,
    onOverlayModeChanged: (Boolean) -> Unit,
    onNavigationBarModeChanged: (ReareyeNavigationBarMode) -> Unit,
    onEffectsEnabledChanged: (Boolean) -> Unit,
    onColorModeChanged: (AppColorMode) -> Unit,
    onThemeStyleChanged: (ThemeStyle) -> Unit,
    onDebugLoggingChanged: (Boolean) -> Unit,
    onAutoReconnectChanged: (Boolean) -> Unit,
    onStrictScanFilterChanged: (Boolean) -> Unit,
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
                onBack = ::closeRoute,
                onNavigationBarModeChanged = onNavigationBarModeChanged,
                onEffectsEnabledChanged = onEffectsEnabledChanged,
                onColorModeChanged = onColorModeChanged,
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
            title = SettingsRoute.Root.title,
            subtitle = SettingsRoute.Root.subtitle,
        )
        ManagerCard(
            title = SettingsRoute.Appearance.title,
            summary = "${renderCapabilities.mode.title} / ${
                when {
                    renderCapabilities.effectsEnabled -> "effects on"
                    else -> "effects off"
                }
            } / ${colorMode.title} / ${themeStyle.title}",
            icon = SettingsRoute.Appearance.icon,
            onClick = { onOpenRoute(SettingsRoute.Appearance) },
        )
        ManagerCard(
            title = SettingsRoute.Protocol.title,
            summary = "${state.preferredProtocol}; strict filter ${if (state.strictSonyScanFilter) "enabled" else "disabled"}",
            icon = SettingsRoute.Protocol.icon,
            onClick = { onOpenRoute(SettingsRoute.Protocol) },
        )
        ManagerCard(
            title = SettingsRoute.Diagnostics.title,
            summary = "${state.debugLogs.size} recent logs; debug ${if (state.debugLogging) "enabled" else "disabled"}",
            icon = SettingsRoute.Diagnostics.icon,
            onClick = { onOpenRoute(SettingsRoute.Diagnostics) },
        )
        ManagerCard(
            title = SettingsRoute.Modules.title,
            summary = "EQ, ambient, wearing detection, Quick Access, Sense, Multipoint, LE Audio, FOTA",
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
    onBack: () -> Unit,
    onNavigationBarModeChanged: (ReareyeNavigationBarMode) -> Unit,
    onEffectsEnabledChanged: (Boolean) -> Unit,
    onColorModeChanged: (AppColorMode) -> Unit,
    onThemeStyleChanged: (ThemeStyle) -> Unit,
) {
    PageColumn(bottomInnerPadding = bottomInnerPadding) {
        RouteHeader(route = SettingsRoute.Appearance, onBack = onBack)
        SectionCard(title = "Navigation surface", icon = Icons.Rounded.Settings) {
            SettingRow(
                title = "Bottom bar mode",
                subtitle = "Normal, translucent, floating, or liquid glass",
                trailing = {
                    SegmentedChoice(
                        selected = renderCapabilities.mode,
                        values = ReareyeNavigationBarMode.entries,
                        label = { it.title },
                        onSelected = onNavigationBarModeChanged,
                    )
                },
            )
            SettingRow(
                title = "UI effects",
                subtitle = "Backdrop blur, lens, glass cards, reveal motion, and About gradient",
                trailing = {
                    Switch(
                        checked = renderCapabilities.userEffectsEnabled,
                        onCheckedChange = onEffectsEnabledChanged,
                    )
                },
            )
            SettingRow(
                title = "Active glass path",
                subtitle = when {
                    !renderCapabilities.userEffectsEnabled -> "Effects disabled; surfaces fall back to plain cards"
                    renderCapabilities.liquidGlassEnabled -> "Floating liquid glass with blur, lens, vibrancy, and drag"
                    renderCapabilities.semiTransparentBottomBar -> "Semi-transparent blur-backed bar"
                    renderCapabilities.floatingBottomBarEnabled -> "Floating capsule without blur"
                    else -> "Opaque Material navigation"
                },
                trailing = {
                    StatusPill(
                        text = when {
                            renderCapabilities.effectsEnabled -> "live"
                            else -> "off"
                        },
                        active = renderCapabilities.effectsEnabled,
                    )
                },
            )
        }
        SectionCard(title = "Theme") {
            SettingRow(
                title = "颜色模式",
                subtitle = "选择浅色、深色，或跟随系统外观",
                trailing = {
                    SegmentedChoice(
                        selected = colorMode,
                        values = AppColorMode.entries,
                        label = { it.title },
                        onSelected = onColorModeChanged,
                    )
                },
            )
            SettingRow(
                title = "Theme style",
                subtitle = "MIUIX-like keeps Material controls but softens surfaces",
                trailing = {
                    SegmentedChoice(
                        selected = themeStyle,
                        values = ThemeStyle.entries,
                        label = { it.title },
                        onSelected = onThemeStyleChanged,
                    )
                },
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
        SectionCard(title = "Protocol") {
            SettingRow(
                title = "Preferred protocol",
                subtitle = state.preferredProtocol,
                trailing = { StatusPill("profile", true) },
            )
            SettingRow(
                title = "Strict Sony scan filter",
                subtitle = "Only accept advertisements with Tandem V2 HPC service UUID",
                trailing = {
                    Switch(
                        checked = state.strictSonyScanFilter,
                        onCheckedChange = onStrictScanFilterChanged,
                    )
                },
            )
            SettingRow(
                title = "Auto reconnect",
                subtitle = "Reserved for the next connection manager pass",
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
        SectionCard(title = "Diagnostics", icon = Icons.Rounded.Code) {
            SettingRow(
                title = "Debug logging",
                subtitle = "Show BLE TX/RX bytes and GATT state changes",
                trailing = {
                    Switch(
                        checked = state.debugLogging,
                        onCheckedChange = onDebugLoggingChanged,
                    )
                },
            )
            state.table2Diagnostic?.let { diagnostic ->
                Text(
                    text = "Last Table2 response",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp),
                )
                InfoLine("Channel", diagnostic.channel)
                InfoLine("Family", diagnostic.family)
                InfoLine("Command", diagnostic.command.hexByteOrUnknown())
                InfoLine("Inquired type", diagnostic.inquiredType?.hexByteOrUnknown() ?: "Unknown")
                InfoLine("Values", diagnostic.values.joinToString(prefix = "[", postfix = "]"))
                InfoLine("Raw", diagnostic.rawHex)
            }
            if (state.debugLogs.isEmpty()) {
                Text(
                    text = "No protocol traffic yet.",
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
) {
    PageColumn(bottomInnerPadding = bottomInnerPadding) {
        RouteHeader(route = SettingsRoute.Modules, onBack = onBack)
        SectionCard(title = "Reserved modules") {
            listOf(
                "EQ / Clear Bass",
                "Ambient sound level",
                "Wearing detection",
                "Quick Access",
                "Sense / AutoPlay",
                "Multipoint / LE Audio / FOTA",
            ).forEach { name ->
                SettingRow(
                    title = name,
                    subtitle = "Interface reserved; implementation pending",
                    trailing = { StatusPill("stub", false) },
                )
            }
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
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = route.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = route.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
