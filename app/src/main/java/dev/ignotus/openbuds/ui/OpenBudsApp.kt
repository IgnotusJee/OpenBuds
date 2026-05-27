package dev.ignotus.openbuds.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import dev.ignotus.openbuds.service.SonyControlService
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.view.WindowCompat
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import dev.ignotus.openbuds.theme.OpenBudsTheme
import dev.ignotus.openbuds.theme.OpenBudsThemeConfig
import dev.ignotus.openbuds.theme.resolveOpenBudsColorScheme
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop as TextureLayerBackdrop
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.textureBlur
import dev.ignotus.openbuds.ble.DiscoveredSonyDevice
import dev.ignotus.openbuds.data.FeatureStatus
import dev.ignotus.openbuds.data.SonyHeadphoneUiState
import dev.ignotus.openbuds.headphones.ConnectedHeadphoneProfile
import dev.ignotus.openbuds.headphones.HeadphoneFeature
import dev.ignotus.openbuds.headphones.HeadphoneFormFactor
import dev.ignotus.openbuds.protocol.EqPresetId
import dev.ignotus.openbuds.protocol.NoiseControlMode
import dev.ignotus.openbuds.protocol.PlaybackStatus

import dev.ignotus.openbuds.ui.screen.HomeScreen
import dev.ignotus.openbuds.ui.screen.SettingsScreen
import dev.ignotus.openbuds.ui.screen.AboutScreen
import dev.ignotus.openbuds.ui.device.DeviceActionCallback
import dev.ignotus.openbuds.ui.device.DevicePage
import dev.ignotus.openbuds.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs
import kotlin.math.sign

private enum class AppRoute(
    val titleResId: Int,
    val icon: ImageVector,
    val quickTitleResId: Int = titleResId,
) {
    Home(R.string.nav_home, Icons.Rounded.Home),
    Device(R.string.nav_device, Icons.Rounded.Bluetooth),
    Settings(R.string.nav_settings, Icons.Rounded.Settings, R.string.nav_quick_config),
    About(R.string.nav_about, Icons.Rounded.Info),
}

enum class ReareyeNavigationBarMode(val titleResId: Int) {
    Normal(R.string.nav_bar_mode_normal),
    SemiTransparent(R.string.nav_bar_mode_semi_transparent),
    Floating(R.string.nav_bar_mode_floating),
    FloatingGlass(R.string.nav_bar_mode_liquid_glass),
}

enum class ThemeStyle(val titleResId: Int) {
    Material(R.string.theme_style_material),
    Miuix(R.string.theme_style_miuix),
}

internal enum class SettingsRoute(
    val titleResId: Int,
    val subtitleResId: Int,
    val icon: ImageVector,
) {
    Root(R.string.settings_root_title, R.string.settings_root_subtitle, Icons.Rounded.Settings),
    Appearance(R.string.settings_appearance_title, R.string.settings_appearance_subtitle, Icons.Rounded.Settings),
    Protocol(R.string.settings_protocol_title, R.string.settings_protocol_subtitle, Icons.Rounded.Bluetooth),
    Diagnostics(R.string.settings_diagnostics_title, R.string.settings_diagnostics_subtitle, Icons.Rounded.Code),
    Modules(R.string.settings_modules_title, R.string.settings_modules_subtitle, Icons.Rounded.Info),
}

internal data class SettingsAnimatedRoute(
    val route: SettingsRoute,
    val depth: Int,
)

internal enum class AboutRoute(
    val titleResId: Int,
    val subtitleResId: Int,
) {
    Root(R.string.about_root_title, R.string.about_root_subtitle),
    Protocol(R.string.about_protocol_title, R.string.about_protocol_subtitle),
}

internal data class AboutAnimatedRoute(
    val route: AboutRoute,
    val depth: Int,
)

private enum class NavigationQuickAction(
    val titleResId: Int,
    val target: SettingsRoute,
    val icon: ImageVector,
) {
    Appearance(R.string.settings_quick_appearance_label, SettingsRoute.Appearance, Icons.Rounded.Settings),
    Diagnostics(R.string.settings_quick_diagnostics_label, SettingsRoute.Diagnostics, Icons.Rounded.Code),
    Modules(R.string.settings_quick_modules_label, SettingsRoute.Modules, Icons.Rounded.Info),
}

internal val LocalTextureBackdrop = staticCompositionLocalOf<TextureLayerBackdrop?> { null }
internal val LocalUiRenderCapabilities = staticCompositionLocalOf {
    UiRenderCapabilities(
        mode = ReareyeNavigationBarMode.Floating,
        tier = EffectsTier.DISABLED,
    )
}

private const val NAV_BAR_EXIT_DURATION_MS = 220L
private const val OVERLAY_ROUTE_EXIT_DURATION_MS = 220L
private val MainScreenOrder = AppRoute.entries

@Composable
fun OpenBudsApp(
    state: SonyHeadphoneUiState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (DiscoveredSonyDevice) -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onSetNoiseControlMode: (NoiseControlMode) -> Unit,
    onSetAmbientLevel: (Int) -> Unit,
    onSetAmbientVoiceMode: (Boolean) -> Unit,
    onSetEqPreset: (EqPresetId) -> Unit,
    onSetClearBass: (Int) -> Unit,
    onSetCustomEqBand: (Int, Int) -> Unit,
    onPlaybackPrevious: () -> Unit,
    onPlaybackPlayPause: () -> Unit,
    onPlaybackNext: () -> Unit,
    onDebugLoggingChanged: (Boolean) -> Unit,
    onAutoReconnectChanged: (Boolean) -> Unit,
    onStrictScanFilterChanged: (Boolean) -> Unit,
) {
    var currentRoute by remember { mutableStateOf(AppRoute.Home) }
    var navBarVisible by remember { mutableStateOf(true) }
    var pendingSettingsTarget by remember { mutableStateOf<SettingsRoute?>(null) }
    var pendingQuickActionTransition by remember { mutableStateOf(false) }
    var settingsRouteStack by remember { mutableStateOf(listOf(SettingsRoute.Root)) }
    var showQuickActionMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember(context) { AppUiSettingsStore(context.applicationContext) }
    val appUiSettings by settingsStore.settings.collectAsState(initial = null)
    val loadedAppUiSettings = appUiSettings
    if (loadedAppUiSettings == null) {
        ApplyAppSystemBars(MaterialTheme.colorScheme, isSystemInDarkTheme())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
        return
    }
    LaunchedEffect(Unit) {
        if (loadedAppUiSettings.serviceBackgroundRun) {
            val intent = Intent(context, SonyControlService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }
    val navigationBarMode = remember(loadedAppUiSettings.navigationBarMode) {
        enumValueOrDefault(loadedAppUiSettings.navigationBarMode, ReareyeNavigationBarMode.Floating)
    }
    val effectsTier = remember(context, loadedAppUiSettings.effectsEnabled) {
        EffectsTierManager.determineTier(context, loadedAppUiSettings.effectsEnabled)
    }
    val renderCapabilities = rememberUiRenderCapabilities(
        mode = navigationBarMode,
        tier = effectsTier,
    )
    val renderEffectsSupported = remember { isRenderEffectSupported() }
    val themeStyle = remember(loadedAppUiSettings.themeStyle) {
        enumValueOrDefault(loadedAppUiSettings.themeStyle, ThemeStyle.Material)
    }
    val colorMode = remember(loadedAppUiSettings.colorMode) {
        enumValueOrDefault(loadedAppUiSettings.colorMode, AppColorMode.System)
    }
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = resolveDarkTheme(colorMode, systemDarkTheme)
    val themeConfig = remember(themeStyle, colorMode, loadedAppUiSettings.seedColorIndex, darkTheme) {
        OpenBudsThemeConfig(
            style = if (themeStyle == ThemeStyle.Miuix) 1 else 0,
            colorMode = when (colorMode) {
                AppColorMode.System -> 0
                AppColorMode.Light -> 1
                AppColorMode.Dark -> 2
            },
            seedColorIndex = loadedAppUiSettings.seedColorIndex,
            darkTheme = darkTheme,
        )
    }
    val appColorScheme = remember(themeConfig) { resolveOpenBudsColorScheme(themeConfig) }

    LaunchedEffect(currentRoute) {
        if (currentRoute != AppRoute.Settings) {
            navBarVisible = true
            pendingSettingsTarget = null
            settingsRouteStack = listOf(SettingsRoute.Root)
        }
    }
    val appContent: @Composable () -> Unit = {
        val lifecycleOwner = LocalLifecycleOwner.current
        val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
        val backdropInLifecycle = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
        val backdrop = if (renderCapabilities.navigationBackdropEnabled && renderEffectsSupported && backdropInLifecycle) {
            rememberLayerBackdrop {
                drawRect(appColorScheme.background)
                drawContent()
            }
        } else {
            null
        }
        val textureBackdrop = rememberTextureBackdrop(
            enabled = renderCapabilities.effectsEnabled &&
                (renderCapabilities.semiTransparentBottomBar || renderCapabilities.glassCardsEnabled),
        )
        var stableBottomInset by remember { mutableStateOf(0.dp) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            CompositionLocalProvider(
                LocalTextureBackdrop provides if (renderCapabilities.glassCardsEnabled) textureBackdrop else null,
                LocalUiRenderCapabilities provides renderCapabilities,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .textureBackdropSource(textureBackdrop)
                        .then(
                            if (renderCapabilities.navigationBackdropEnabled && backdrop != null) {
                                Modifier.layerBackdrop(backdrop)
                            } else {
                                Modifier
                            }
                        )
                ) {
                    AnimatedContent(
                        targetState = currentRoute,
                        contentKey = { it.name },
                        transitionSpec = {
                            if (pendingQuickActionTransition && targetState == AppRoute.Settings) {
                                pendingQuickActionTransition = false
                                ContentTransform(
                                    targetContentEnter = EnterTransition.None,
                                    initialContentExit = ExitTransition.None,
                                )
                            } else {
                                val initialIndex = MainScreenOrder.indexOf(initialState).coerceAtLeast(0)
                                val targetIndex = MainScreenOrder.indexOf(targetState).coerceAtLeast(0)
                                reareyeHorizontalTransform(forward = targetIndex >= initialIndex)
                            }
                        },
                        label = "ScreenTransition",
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing),
                    ) { route ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (route) {
                                AppRoute.Home -> HomeScreen(
                                    state = state,
                                    bottomInnerPadding = stableBottomInset,
                                    onOpenDevice = { currentRoute = AppRoute.Device },
                                    onRefresh = onRefresh,
                                )
                                AppRoute.Device -> {
                                    val deviceActions = remember(state, stableBottomInset,
                                        onStartScan, onStopScan, onConnect, onDisconnect, onRefresh,
                                        onSetNoiseControlMode, onSetAmbientLevel, onSetAmbientVoiceMode,
                                        onSetEqPreset, onSetClearBass, onSetCustomEqBand,
                                        onPlaybackPrevious, onPlaybackPlayPause, onPlaybackNext,
                                    ) {
                                        DeviceActionCallback(
                                            onStartScan = onStartScan,
                                            onStopScan = onStopScan,
                                            onConnect = onConnect,
                                            onDisconnect = onDisconnect,
                                            onRefresh = onRefresh,
                                            onSetNoiseControlMode = onSetNoiseControlMode,
                                            onSetAmbientLevel = onSetAmbientLevel,
                                            onSetAmbientVoiceMode = onSetAmbientVoiceMode,
                                            onSetEqPreset = onSetEqPreset,
                                            onSetClearBass = onSetClearBass,
                                            onSetCustomEqBand = onSetCustomEqBand,
                                            onPlaybackPrevious = onPlaybackPrevious,
                                            onPlaybackPlayPause = onPlaybackPlayPause,
                                            onPlaybackNext = onPlaybackNext,
                                        )
                                    }
                                    DevicePage(
                                        state = state,
                                        bottomInnerPadding = stableBottomInset,
                                        actions = deviceActions,
                                    )
                                }
                                AppRoute.Settings -> SettingsScreen(
                                    state = state,
                                    bottomInnerPadding = stableBottomInset,
                                    renderCapabilities = renderCapabilities,
                                    themeStyle = themeStyle,
                                    colorMode = colorMode,
                                    routeStack = settingsRouteStack,
                                    quickTarget = pendingSettingsTarget,
                                    onQuickTargetConsumed = { pendingSettingsTarget = null },
                                    onRouteStackChanged = { settingsRouteStack = it },
                                    onOverlayModeChanged = { navBarVisible = !it },
                                    onNavigationBarModeChanged = {
                                        scope.launch { settingsStore.setNavigationBarMode(it) }
                                    },
                                    onEffectsEnabledChanged = {
                                        scope.launch { settingsStore.setEffectsEnabled(it) }
                                    },
                                    onColorModeChanged = {
                                        scope.launch { settingsStore.setColorMode(it) }
                                    },
                                    seedColorIndex = loadedAppUiSettings.seedColorIndex,
                                    onSeedColorIndexChanged = {
                                        scope.launch { settingsStore.setSeedColorIndex(it) }
                                    },
                                    onThemeStyleChanged = {
                                        scope.launch { settingsStore.setThemeStyle(it) }
                                    },
                                    onDebugLoggingChanged = onDebugLoggingChanged,
                                    onAutoReconnectChanged = onAutoReconnectChanged,
                                    onStrictScanFilterChanged = onStrictScanFilterChanged,
                                    serviceBackgroundRun = loadedAppUiSettings.serviceBackgroundRun,
                                    notificationPersistent = loadedAppUiSettings.notificationPersistent,
                                    connectionPopup = loadedAppUiSettings.connectionPopup,
                                    hyperOsNotification = loadedAppUiSettings.hyperOsNotification,
                                    controlCenterIntercept = loadedAppUiSettings.controlCenterIntercept,
                                    onServiceBackgroundRunChanged = { enabled ->
                                        scope.launch { settingsStore.setServiceBackgroundRun(enabled) }
                                        val intent = Intent(context, SonyControlService::class.java)
                                        if (enabled) {
                                            ContextCompat.startForegroundService(context, intent)
                                        } else {
                                            context.stopService(intent)
                                        }
                                    },
                                    onNotificationPersistentChanged = {
                                        scope.launch { settingsStore.setNotificationPersistent(it) }
                                    },
                                    onConnectionPopupChanged = {
                                        scope.launch { settingsStore.setConnectionPopup(it) }
                                    },
                                    onHyperOsNotificationChanged = {
                                        scope.launch { settingsStore.setHyperOsNotification(it) }
                                    },
                                    onControlCenterInterceptChanged = {
                                        scope.launch { settingsStore.setControlCenterIntercept(it) }
                                    },
                                )
                                AppRoute.About -> AboutScreen(
                                    state = state,
                                    bottomInnerPadding = stableBottomInset,
                                    themeStyle = themeStyle,
                                    renderCapabilities = renderCapabilities,
                                )
                            }
                        }
                    }
                }
                AppNavigationBar(
                    selectedRoute = currentRoute,
                    renderCapabilities = renderCapabilities,
                    backdrop = backdrop,
                    textureBackdrop = textureBackdrop,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onMeasuredHeight = { stableBottomInset = it },
                    visible = navBarVisible,
                    onSelected = { currentRoute = it },
                    onQuickActionRequested = { showQuickActionMenu = true },
                )
                if (showQuickActionMenu) {
                    NavigationQuickActionPopup(
                        renderCapabilities = renderCapabilities,
                        navigationBackdrop = backdrop,
                        onDismiss = { showQuickActionMenu = false },
                        onSelected = { action ->
                            showQuickActionMenu = false
                            pendingQuickActionTransition = currentRoute != AppRoute.Settings
                            pendingSettingsTarget = action.target
                            currentRoute = AppRoute.Settings
                        },
                    )
                }
            }
        }
    }
    OpenBudsTheme(config = themeConfig) {
        ApplyAppSystemBars(appColorScheme, darkTheme)
        appContent()
    }
}

@Composable
private fun AppNavigationBar(
    selectedRoute: AppRoute,
    renderCapabilities: UiRenderCapabilities,
    backdrop: Backdrop?,
    textureBackdrop: TextureLayerBackdrop?,
    modifier: Modifier = Modifier,
    visible: Boolean,
    onMeasuredHeight: (Dp) -> Unit,
    onSelected: (AppRoute) -> Unit,
    onQuickActionRequested: () -> Unit,
) {
    val density = LocalDensity.current
    val transitionProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (visible) 380 else 240,
            easing = if (visible) FastOutSlowInEasing else FastOutLinearInEasing,
        ),
        label = "NavigationVisibility",
    )
    Box(
        modifier = modifier.graphicsLayer {
            alpha = transitionProgress
            translationY = (1f - transitionProgress) * with(density) { 28.dp.toPx() }
        }
    ) {
    if (!renderCapabilities.floatingBottomBarEnabled) {
        val containerColor = MaterialTheme.colorScheme.surface.copy(
            alpha = when {
                renderCapabilities.semiTransparentBottomBar && textureBackdrop != null -> 0.87f
                renderCapabilities.semiTransparentBottomBar -> 0.72f
                else -> 1f
            },
        )
        val bar: @Composable () -> Unit = {
            NavigationBar(
                modifier = Modifier
                    .navigationBarsPadding()
                    .onGloballyPositioned { coordinates ->
                        onMeasuredHeight(with(density) { coordinates.size.height.toDp() })
                    },
                tonalElevation = 0.dp,
                containerColor = Color.Transparent,
            ) {
                AppRoute.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedRoute == tab,
                        onClick = { onSelected(tab) },
                        enabled = visible,
                        icon = { Icon(tab.icon, contentDescription = stringResource(tab.titleResId)) },
                        label = { Text(stringResource(tab.titleResId)) },
                    )
                }
            }
        }
        if (renderCapabilities.semiTransparentBottomBar && textureBackdrop != null) {
            BlurredBar(backdrop = textureBackdrop) {
                bar()
            }
        } else {
            Box(modifier = Modifier.background(containerColor)) {
                bar()
            }
        }
    } else {
        FloatingLiquidNavigationBar(
            selectedRoute = selectedRoute,
            backdrop = backdrop,
            blurEnabled = renderCapabilities.navigationBackdropEnabled,
            liquidGlassEnabled = renderCapabilities.liquidGlassEnabled,
            modifier = Modifier,
            shadowAlpha = transitionProgress,
            interactionsEnabled = visible,
            onMeasuredHeight = onMeasuredHeight,
            onSelected = onSelected,
            onQuickActionRequested = onQuickActionRequested,
        )
    }
    }
}

@Composable
private fun NavigationQuickActionPopup(
    renderCapabilities: UiRenderCapabilities,
    navigationBackdrop: Backdrop?,
    onDismiss: () -> Unit,
    onSelected: (NavigationQuickAction) -> Unit,
) {
    val density = LocalDensity.current
    val actions = NavigationQuickAction.entries
    val isLight = MaterialTheme.colorScheme.background.luminance() >= 0.5f
    val style = rememberQuickActionStyle(renderCapabilities, isLight)
    val useGlass = style.useGlass && navigationBackdrop != null
    val menuBackdrop = if (useGlass) rememberLayerBackdrop() else null
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
    }
    val dismiss: () -> Unit = {
        scope.launch {
            progress.animateTo(0f, tween(170, easing = FastOutSlowInEasing))
            onDismiss()
        }
    }

    Popup(
        alignment = Alignment.BottomCenter,
        onDismissRequest = dismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        val progressValue = progress.value
        Box(
            modifier = Modifier
                .graphicsLayer {
                    alpha = progressValue
                    val scale = 0.92f + 0.08f * progressValue
                    scaleX = scale
                    scaleY = scale
                    translationY = with(density) { (1f - progressValue) * 8.dp.toPx() }
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            // Hidden backdrop source layer for glass mode — provides capsule surfaces
            // that the menuBackdrop samples for its blur+vibrancy base
            if (menuBackdrop != null) {
                Column(
                    modifier = Modifier.layerBackdrop(menuBackdrop),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    repeat(actions.size) {
                        Box(
                            modifier = Modifier
                                .width(176.dp)
                                .height(40.dp)
                                .background(style.containerColor, ContinuousCapsule),
                        )
                    }
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 92.dp),
            ) {
                actions.forEachIndexed { index, action ->
                    val delayProgress = (progressValue - index * 0.05f).coerceIn(0f, 1f)
                    QuickActionButton(
                        label = stringResource(action.titleResId),
                        subtitle = stringResource(action.target.subtitleResId),
                        icon = action.icon,
                        style = style,
                        menuBackdrop = menuBackdrop,
                        navigationBackdrop = navigationBackdrop,
                        delayProgress = delayProgress,
                        onClick = {
                            scope.launch {
                                progress.animateTo(0f, tween(120, easing = FastOutSlowInEasing))
                                onSelected(action)
                            }
                        },
                    )
                }
            }
        }
    }
}

private data class QuickActionStyle(
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color,
    val shadowAlpha: Float,
    val shadowElevation: Dp,
    val useGlass: Boolean,
)

@Composable
private fun rememberQuickActionStyle(
    renderCapabilities: UiRenderCapabilities,
    isLight: Boolean,
): QuickActionStyle {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    return when {
        renderCapabilities.liquidGlassEnabled -> QuickActionStyle(
            containerColor = if (isLight) {
                Color.White.copy(alpha = 0.18f)
            } else {
                Color.White.copy(alpha = 0.105f)
            },
            contentColor = onSurface,
            borderColor = Color.White.copy(alpha = if (isLight) 0.34f else 0.15f),
            shadowAlpha = if (isLight) 0.14f else 0.30f,
            shadowElevation = 16.dp,
            useGlass = true,
        )
        renderCapabilities.floatingBottomBarEnabled -> QuickActionStyle(
            containerColor = surface,
            contentColor = onSurface,
            borderColor = outline.copy(alpha = if (isLight) 0.70f else 0.86f),
            shadowAlpha = if (isLight) 0.18f else 0.34f,
            shadowElevation = 14.dp,
            useGlass = false,
        )
        else -> QuickActionStyle(
            containerColor = surface,
            contentColor = onSurface,
            borderColor = outline.copy(alpha = if (isLight) 0.70f else 0.86f),
            shadowAlpha = if (isLight) 0.13f else 0.28f,
            shadowElevation = 10.dp,
            useGlass = false,
        )
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    subtitle: String,
    icon: ImageVector,
    style: QuickActionStyle,
    menuBackdrop: Backdrop?,
    navigationBackdrop: Backdrop?,
    delayProgress: Float,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val useGlass = style.useGlass && menuBackdrop != null && navigationBackdrop != null
    val progressScale = 0.96f + 0.04f * delayProgress

    Box(
        modifier = Modifier
            .width(176.dp)
            .height(40.dp)
            .graphicsLayer {
                val scaleValue = progressScale
                scaleX = scaleValue
                scaleY = scaleValue
                shape = ContinuousCapsule
                clip = true
                shadowElevation = with(density) { style.shadowElevation.toPx() }
                ambientShadowColor = Color.Black.copy(alpha = style.shadowAlpha)
                spotShadowColor = Color.Black.copy(alpha = style.shadowAlpha)
            }
            .then(
                if (useGlass) {
                    Modifier.drawBackdrop(
                        backdrop = menuBackdrop!!,
                        shape = { ContinuousCapsule },
                        effects = {
                            vibrancy()
                            blur(12f.dp.toPx())
                            lens(28f.dp.toPx(), 30f.dp.toPx())
                        },
                        highlight = { Highlight.Default.copy(alpha = 0.92f) },
                        shadow = {
                            Shadow.Default.copy(
                                color = Color.Black.copy(alpha = 0.22f),
                                alpha = 0.40f,
                            )
                        },
                        innerShadow = {
                            InnerShadow(radius = 8.dp, alpha = 0.28f)
                        },
                        onDrawSurface = {
                            drawRect(style.containerColor)
                            drawRect(Color.White.copy(alpha = 0.055f))
                            drawRect(Color.Black.copy(alpha = 0.012f))
                        },
                    )
                } else {
                    Modifier
                        .background(style.containerColor, ContinuousCapsule)
                }
            )
            .then(
                Modifier.border(0.75.dp, style.borderColor, ContinuousCapsule)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = style.contentColor,
                modifier = Modifier.size(19.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FloatingLiquidNavigationBar(
    selectedRoute: AppRoute,
    backdrop: Backdrop?,
    blurEnabled: Boolean,
    liquidGlassEnabled: Boolean,
    modifier: Modifier = Modifier,
    shadowAlpha: Float,
    interactionsEnabled: Boolean,
    onMeasuredHeight: (Dp) -> Unit,
    onSelected: (AppRoute) -> Unit,
    onQuickActionRequested: () -> Unit,
) {
    val tabs = AppRoute.entries
    val selectedIndex = tabs.indexOf(selectedRoute).coerceAtLeast(0)
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val isLight = MaterialTheme.colorScheme.background.luminance() >= 0.5f
    val navRenderCapabilities = LocalUiRenderCapabilities.current
    val containerColor = if (blurEnabled) {
        MaterialTheme.colorScheme.surface.copy(alpha = if (isLight) 0.40f else 0.34f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = if (isLight) 0.88f else 0.85f)
    }
    val tabsBackdrop = if (liquidGlassEnabled && backdrop != null) rememberLayerBackdrop() else null
    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var currentIndex by remember { mutableIntStateOf(selectedIndex) }
    val offsetAnimation = remember { Animatable(0f) }
    val panelOffsetPx by remember(density) {
        derivedStateOf {
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                with(density) {
                    4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }
    }
    class DampedDragAnimationHolder {
        var instance: DampedDragAnimation? = null
    }
    val holder = remember { DampedDragAnimationHolder() }
    val dampedDragAnimation = remember(animationScope, tabs.size, density, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..tabs.lastIndex.toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            canDrag = { offset ->
                val anim = holder.instance ?: return@DampedDragAnimation true
                if (tabWidthPx == 0f) return@DampedDragAnimation false
                val currentValue = anim.value
                val indicatorX = currentValue * tabWidthPx
                val padding = with(density) { 4.dp.toPx() }
                val globalTouchX = if (isLtr) {
                    val touchX = indicatorX + offset.x
                    padding + touchX
                } else {
                    totalWidthPx - padding - tabWidthPx - indicatorX + offset.x
                }
                globalTouchX in 0f..totalWidthPx
            },
            onDragStarted = {},
            onDragStopped = {
                val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabs.lastIndex)
                currentIndex = targetIndex
                animateToValue(targetIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f) {
                    val direction = if (isLtr) 1f else -1f
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * direction)
                            .fastCoerceIn(0f, tabs.lastIndex.toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            },
        ).also { holder.instance = it }
    }
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope)
    }

    LaunchedEffect(selectedIndex) {
        if (currentIndex != selectedIndex) {
            currentIndex = selectedIndex
            dampedDragAnimation.animateToValue(selectedIndex.toFloat())
        }
    }
    LaunchedEffect(currentIndex) {
        if (currentIndex != selectedIndex) {
            onSelected(tabs[currentIndex])
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .onGloballyPositioned { coordinates ->
                onMeasuredHeight(with(density) { coordinates.size.height.toDp() })
            }
            .padding(
                bottom = 12.dp,
                start = 18.dp,
                end = 18.dp,
                top = 8.dp,
            ),
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .height(64.dp)
                .onGloballyPositioned { coordinates ->
                    totalWidthPx = coordinates.size.width.toFloat()
                    tabWidthPx = (totalWidthPx - with(density) { 8.dp.toPx() }) / tabs.size
                }
                .graphicsLayer { clip = false }
                .graphicsLayer { translationX = panelOffsetPx }
                .then(
                    if (blurEnabled && backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { ContinuousCapsule },
                            effects = {
                                vibrancy()
                                blur(8f.dp.toPx())
                                if (liquidGlassEnabled) {
                                    lens(24f.dp.toPx(), 24f.dp.toPx())
                                }
                            },
                            highlight = {
                                Highlight.Default.copy(alpha = if (isLight) 0.9f else 0.55f)
                            },
                            shadow = {
                                Shadow.Default.copy(
                                    color = Color.Black.copy(alpha = if (isLight) 0.12f else 0.26f),
                                    alpha = shadowAlpha,
                                )
                            },
                            layerBlock = {
                                if (liquidGlassEnabled) {
                                    val progress = dampedDragAnimation.pressProgress
                                    val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                                    scaleX = scale
                                    scaleY = scale
                                }
                            },
                            onDrawSurface = {
                                drawRect(containerColor)
                            },
                        )
                    } else {
                        Modifier
                            .shadow(
                                elevation = 14.dp,
                                shape = ContinuousCapsule,
                                ambientColor = Color.Black.copy(alpha = (if (isLight) 0.10f else 0.24f) * shadowAlpha),
                                spotColor = Color.Black.copy(alpha = (if (isLight) 0.12f else 0.28f) * shadowAlpha),
                            )
                            .clip(ContinuousCapsule)
                            .background(containerColor)
                    }
                )
                .padding(4.dp)
                .then(interactiveHighlight.modifier),
        ) {
            if (liquidGlassEnabled && backdrop != null && tabsBackdrop != null) {
                val duplicateProgress = dampedDragAnimation.pressProgress
                Row(
                    modifier = Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer { translationX = panelOffsetPx }
                        .then(interactiveHighlight.gestureModifier)
                        .fillMaxWidth()
                        .height(56.dp)
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { ContinuousCapsule },
                            effects = {
                                vibrancy()
                                if (blurEnabled) {
                                    blur(8f.dp.toPx())
                                }
                                lens(24f.dp.toPx() * duplicateProgress, 24f.dp.toPx() * duplicateProgress)
                            },
                            highlight = {
                                Highlight.Default.copy(alpha = duplicateProgress)
                            },
                            onDrawSurface = {
                                drawRect(containerColor)
                            },
                        )
                        .graphicsLayer(colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEach { tab ->
                        NavigationTabContent(
                            tab = tab,
                            selected = selectedRoute == tab,
                             modifier = Modifier.weight(1f),
                             scale = { lerp(1f, 1.2f, duplicateProgress) },
                             clickEnabled = false,
                             onSelected = onSelected,
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .then(interactiveHighlight.gestureModifier),
            ) {
                tabs.forEach { tab ->
                        NavigationTabContent(
                            tab = tab,
                            selected = selectedRoute == tab,
                            modifier = Modifier.weight(1f),
                            clickEnabled = interactionsEnabled,
                            onSelected = {
                            if (it != selectedRoute) {
                                onSelected(it)
                            }
                        },
                        onLongPress = if (interactionsEnabled && tab == AppRoute.Settings) {
                            onQuickActionRequested
                        } else {
                            null
                        },
                    )
                }
            }
            if (tabWidthPx > 0f) {
                val selectedIndicatorBackdrop = tabsBackdrop
                val combinedForSelected = if (liquidGlassEnabled && backdrop != null && selectedIndicatorBackdrop != null) {
                    rememberCombinedBackdrop(backdrop, selectedIndicatorBackdrop)
                } else {
                    null
                }
                Box(
                    modifier = Modifier
                        .width(with(density) { tabWidthPx.toDp() })
                        .height(56.dp)
                        .graphicsLayer {
                            val progressOffset = dampedDragAnimation.value * tabWidthPx
                            translationX = if (isLtr) {
                                progressOffset + panelOffsetPx
                            } else {
                                -progressOffset + panelOffsetPx
                            }
                        }
                        .then(if (liquidGlassEnabled && interactionsEnabled) dampedDragAnimation.modifier else Modifier)
                        .then(
                            if (liquidGlassEnabled && combinedForSelected != null) {
                                Modifier.drawBackdrop(
                                    backdrop = combinedForSelected,
                                    shape = { ContinuousCapsule },
                                    effects = {
                                        val progress = dampedDragAnimation.pressProgress
                                        if (navRenderCapabilities.vibrancyEnabled) {
                                            vibrancy()
                                        }
                                        if (navRenderCapabilities.navigationBackdropEnabled) {
                                            blur(8f.dp.toPx())
                                        }
                                        lens(10f.dp.toPx() * progress, 14f.dp.toPx() * progress, true)
                                    },
                                    highlight = {
                                        Highlight.Default.copy(
                                            alpha = if (isLight) {
                                                0.7f * dampedDragAnimation.pressProgress
                                            } else {
                                                0.35f * dampedDragAnimation.pressProgress
                                            },
                                        )
                                    },
                                    shadow = {
                                        Shadow(alpha = dampedDragAnimation.pressProgress)
                                    },
                                    innerShadow = {
                                        InnerShadow(
                                            radius = 8f.dp * dampedDragAnimation.pressProgress,
                                            alpha = if (isLight) {
                                                0.22f * dampedDragAnimation.pressProgress
                                            } else {
                                                0.34f * dampedDragAnimation.pressProgress
                                            },
                                        )
                                    },
                                    layerBlock = {
                                        if (liquidGlassEnabled) {
                                            scaleX = dampedDragAnimation.scaleX
                                            scaleY = dampedDragAnimation.scaleY
                                            val velocity = dampedDragAnimation.velocity / 10f
                                            scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                            scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                                        }
                                    },
                                    onDrawSurface = {
                                        val progress = dampedDragAnimation.pressProgress
                                        drawRect(
                                            color = if (isLight) {
                                                Color.Black.copy(alpha = 0.10f)
                                            } else {
                                                Color.White.copy(alpha = 0.12f)
                                            },
                                            alpha = 1f - progress,
                                        )
                                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                                    },
                                )
                            } else {
                                Modifier
                                    .clip(ContinuousCapsule)
                                    .background(
                                        if (isLight) {
                                            Color.Black.copy(alpha = 0.10f)
                                        } else {
                                            Color.White.copy(alpha = 0.12f)
                                        }
                                    )
                            }
                        )
                )
            }
        }
    }
}

@Composable
private fun NavigationTabContent(
    tab: AppRoute,
    selected: Boolean,
    modifier: Modifier = Modifier,
    scale: () -> Float = { 1f },
    clickEnabled: Boolean = true,
    onLongPress: (() -> Unit)? = null,
    onSelected: (AppRoute) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .graphicsLayer {
                val scaleValue = scale()
                scaleX = scaleValue
                scaleY = scaleValue
            }
            .clip(ContinuousCapsule)
            .then(
                if (!clickEnabled) {
                    Modifier
                } else if (onLongPress != null) {
                    Modifier.combinedClickable(
                        onClick = { onSelected(tab) },
                        onLongClick = onLongPress,
                    )
                } else {
                    Modifier.clickable { onSelected(tab) }
                }
            )
            .padding(vertical = 7.dp),
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = stringResource(tab.titleResId),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(tab.titleResId),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun ApplyAppSystemBars(colorScheme: ColorScheme, darkTheme: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
