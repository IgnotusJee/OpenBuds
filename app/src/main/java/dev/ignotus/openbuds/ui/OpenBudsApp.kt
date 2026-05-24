package dev.ignotus.openbuds.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import android.os.Build
import android.util.LruCache
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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.asImageBitmap
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
import dev.ignotus.openbuds.theme.openbudsColorScheme
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop as TextureLayerBackdrop
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
import dev.ignotus.openbuds.ui.screen.DeviceScreen
import dev.ignotus.openbuds.ui.screen.SettingsScreen
import dev.ignotus.openbuds.ui.screen.AboutScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.sign

private enum class AppRoute(
    val title: String,
    val icon: ImageVector,
    val quickTitle: String = title,
) {
    Home("Home", Icons.Rounded.Home),
    Device("Device", Icons.Rounded.Bluetooth),
    Settings("Settings", Icons.Rounded.Settings, "Config"),
    About("About", Icons.Rounded.Info),
}

enum class ReareyeNavigationBarMode(val title: String) {
    Normal("Normal"),
    SemiTransparent("Semi transparent"),
    Floating("Floating"),
    FloatingGlass("Liquid glass"),
}

enum class ThemeStyle(val title: String) {
    Material("Material"),
    Miuix("MIUIX"),
}

internal enum class SettingsRoute(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    Root("Settings", "Global app behavior and diagnostics", Icons.Rounded.Settings),
    Appearance("Appearance", "Navigation, glass, motion, and theme", Icons.Rounded.Settings),
    Protocol("Protocol", "Connection defaults and Sony endpoint behavior", Icons.Rounded.Bluetooth),
    Diagnostics("Diagnostics", "Debug logs and current protocol activity", Icons.Rounded.Code),
    Modules("Reserved modules", "Future Sony feature surfaces kept visible", Icons.Rounded.Info),
}

internal data class SettingsAnimatedRoute(
    val route: SettingsRoute,
    val depth: Int,
)

internal enum class AboutRoute(
    val title: String,
    val subtitle: String,
) {
    Root("OpenBuds", "Clean-room Sony headphone controller"),
    Protocol("Protocol references", "Local notes and extracted protocol layers"),
}

internal data class AboutAnimatedRoute(
    val route: AboutRoute,
    val depth: Int,
)

private enum class NavigationQuickAction(
    val title: String,
    val target: SettingsRoute,
    val icon: ImageVector,
) {
    Appearance("Appearance", SettingsRoute.Appearance, Icons.Rounded.Settings),
    Diagnostics("Diagnostics", SettingsRoute.Diagnostics, Icons.Rounded.Code),
    Modules("Modules", SettingsRoute.Modules, Icons.Rounded.Info),
}

internal val LocalTextureBackdrop = staticCompositionLocalOf<TextureLayerBackdrop?> { null }
internal val LocalUiRenderCapabilities = staticCompositionLocalOf {
    UiRenderCapabilities(
        mode = ReareyeNavigationBarMode.Floating,
        userEffectsEnabled = false,
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
    val navigationBarMode = remember(loadedAppUiSettings.navigationBarMode) {
        enumValueOrDefault(loadedAppUiSettings.navigationBarMode, ReareyeNavigationBarMode.Floating)
    }
    val renderCapabilities = rememberUiRenderCapabilities(
        mode = navigationBarMode,
        userEffectsEnabled = loadedAppUiSettings.effectsEnabled,
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
    val baseColorScheme = openbudsColorScheme(darkTheme)
    val appColorScheme = remember(themeStyle, baseColorScheme, darkTheme) {
        if (themeStyle == ThemeStyle.Miuix) {
            miuixLikeColorScheme(baseColorScheme, darkTheme)
        } else {
            baseColorScheme
        }
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute != AppRoute.Settings) {
            navBarVisible = true
            pendingSettingsTarget = null
            settingsRouteStack = listOf(SettingsRoute.Root)
        }
    }
    val appContent: @Composable () -> Unit = {
        val backdrop = if (renderCapabilities.navigationBackdropEnabled && renderEffectsSupported) {
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .textureBackdropSource(textureBackdrop)
                    .background(appColorScheme.background),
            )
            CompositionLocalProvider(
                LocalTextureBackdrop provides if (renderCapabilities.glassCardsEnabled) textureBackdrop else null,
                LocalUiRenderCapabilities provides renderCapabilities,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
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
                                AppRoute.Device -> DeviceScreen(
                                    state = state,
                                    bottomInnerPadding = stableBottomInset,
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
                                    onThemeStyleChanged = {
                                        scope.launch { settingsStore.setThemeStyle(it) }
                                    },
                                    onDebugLoggingChanged = onDebugLoggingChanged,
                                    onAutoReconnectChanged = onAutoReconnectChanged,
                                    onStrictScanFilterChanged = onStrictScanFilterChanged,
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
    MaterialTheme(colorScheme = appColorScheme) {
        ApplyAppSystemBars(appColorScheme, darkTheme)
        if (themeStyle == ThemeStyle.Miuix) {
            MiuixTheme(top.yukonga.miuix.kmp.theme.ThemeController(if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light)) {
                appContent()
            }
        } else {
            appContent()
        }
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
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
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
                        label = action.title,
                        subtitle = action.target.subtitle,
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
                .padding(4.dp),
        ) {
            if (liquidGlassEnabled && backdrop != null && tabsBackdrop != null) {
                val duplicateProgress = dampedDragAnimation.pressProgress
                Row(
                    modifier = Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer { translationX = panelOffsetPx }
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
                modifier = Modifier.fillMaxSize(),
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
            contentDescription = tab.title,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = tab.title,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private fun miuixLikeColorScheme(base: ColorScheme, darkTheme: Boolean): ColorScheme =
    if (darkTheme) {
        base.copy(
            primary = Color(0xFF8AB4FF),
            onPrimary = Color(0xFF062D5F),
            secondary = Color(0xFFC2CAD6),
            tertiary = Color(0xFF77D0BE),
            background = Color(0xFF101114),
            onBackground = Color(0xFFE7E8EC),
            surface = Color(0xFF18191D),
            onSurface = Color(0xFFE7E8EC),
            surfaceVariant = Color(0xFF25272D),
            onSurfaceVariant = Color(0xFFC5C8D0),
            outline = Color(0xFF676B75),
        )
    } else {
        base.copy(
            primary = Color(0xFF1F6FEB),
            onPrimary = Color.White,
            secondary = Color(0xFF5E6B7A),
            tertiary = Color(0xFF0F8F7A),
            background = Color(0xFFF7F8FA),
            onBackground = Color(0xFF181A20),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF181A20),
            surfaceVariant = Color(0xFFECEFF4),
            onSurfaceVariant = Color(0xFF59616E),
            outline = Color(0xFFC7CCD4),
        )
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
