package dev.ignotus.sonyrebuild.ui

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
import androidx.compose.foundation.clickable
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
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop as TextureLayerBackdrop
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import dev.ignotus.sonyrebuild.ble.DiscoveredSonyDevice
import dev.ignotus.sonyrebuild.data.FeatureStatus
import dev.ignotus.sonyrebuild.data.SonyHeadphoneUiState
import dev.ignotus.sonyrebuild.headphones.ConnectedHeadphoneProfile
import dev.ignotus.sonyrebuild.headphones.HeadphoneFeature
import dev.ignotus.sonyrebuild.headphones.HeadphoneFormFactor
import dev.ignotus.sonyrebuild.protocol.EqPresetId
import dev.ignotus.sonyrebuild.protocol.NoiseControlMode
import dev.ignotus.sonyrebuild.protocol.PlaybackStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.roundToInt
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

private enum class SettingsRoute(
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

private data class SettingsAnimatedRoute(
    val route: SettingsRoute,
    val depth: Int,
)

private enum class AboutRoute(
    val title: String,
    val subtitle: String,
) {
    Root("SonyRebuild", "Clean-room Sony headphone controller"),
    Protocol("Protocol references", "Local notes and extracted protocol layers"),
}

private data class AboutAnimatedRoute(
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

private val LocalAppBackdrop = staticCompositionLocalOf<Backdrop?> { null }
private val LocalTextureBackdrop = staticCompositionLocalOf<TextureLayerBackdrop?> { null }
private val LocalUiRenderCapabilities = staticCompositionLocalOf {
    UiRenderCapabilities(
        mode = ReareyeNavigationBarMode.Floating,
        userEffectsEnabled = false,
    )
}

private const val NAV_BAR_EXIT_DURATION_MS = 220L
private const val OVERLAY_ROUTE_EXIT_DURATION_MS = 220L
private val MainScreenOrder = AppRoute.entries

@Composable
fun SonyRebuildApp(
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
        ApplyAppSystemBars(MaterialTheme.colorScheme)
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
    val baseColorScheme = MaterialTheme.colorScheme
    val darkTheme = baseColorScheme.background.luminance() < 0.5f
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
                .background(MaterialTheme.colorScheme.background)
                .graphicsLayer { clip = true },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .textureBackdropSource(textureBackdrop)
                    .background(appColorScheme.background),
            )
            CompositionLocalProvider(
                LocalAppBackdrop provides backdrop,
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
        ApplyAppSystemBars(appColorScheme)
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
    onDismiss: () -> Unit,
    onSelected: (NavigationQuickAction) -> Unit,
) {
    Popup(
        alignment = Alignment.BottomCenter,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 92.dp)
                .widthIn(max = 360.dp),
        ) {
            NavigationQuickAction.entries.forEach { action ->
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelected(action) },
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
                        ) {
                            Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = action.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = action.target.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
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
    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isLight) 0.40f else 0.34f)
    val tabsBackdrop = if (liquidGlassEnabled && backdrop != null) rememberLayerBackdrop() else null
    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    var currentIndex by remember { mutableIntStateOf(selectedIndex) }
    val tabWidthPx = if (totalWidthPx > 0f) (totalWidthPx - with(density) { 8.dp.toPx() }) / tabs.size else 0f
    val offsetAnimation = remember { Animatable(0f) }
    val panelOffsetPx by remember(density) {
        derivedStateOf {
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).coerceIn(-1f, 1f)
                with(density) {
                    4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }
    }
    val dampedDragAnimation = remember(animationScope, tabs.size, density, isLtr, tabWidthPx, totalWidthPx) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..tabs.lastIndex.toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            onDragStarted = { position ->
                if (tabWidthPx > 0f) {
                    val direction = if (isLtr) 1f else -1f
                    val touchOffsetFromCenter = position.x - tabWidthPx / 2f
                    snapToValue(
                        (value + touchOffsetFromCenter / tabWidthPx * direction)
                            .coerceIn(0f, tabs.lastIndex.toFloat())
                    )
                }
            },
            onDragStopped = {
                val targetIndex = targetValue.roundToInt().coerceIn(0, tabs.lastIndex)
                currentIndex = targetIndex
                animateToValue(targetIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f) {
                    val direction = if (isLtr) 1f else -1f
                    snapToValue(
                        (value + dragAmount.x / tabWidthPx * direction)
                            .coerceIn(0f, tabs.lastIndex.toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            },
        )
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
                Row(
                    modifier = Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .fillMaxWidth()
                        .height(56.dp)
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { ContinuousCapsule },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                vibrancy()
                                if (blurEnabled) {
                                    blur(8f.dp.toPx())
                                }
                                lens(24f.dp.toPx() * progress, 24f.dp.toPx() * progress)
                            },
                            highlight = {
                                Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress)
                            },
                            onDrawSurface = {
                                drawRect(containerColor)
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEach { tab ->
                        NavigationTabContent(
                            tab = tab,
                            selected = selectedRoute == tab,
                             modifier = Modifier.weight(1f),
                             scale = { lerp(1f, 1.2f, dampedDragAnimation.pressProgress) },
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
                Box(
                    modifier = Modifier
                        .width(with(density) { tabWidthPx.toDp() })
                        .height(56.dp)
                        .graphicsLayer {
                            val progressOffset = dampedDragAnimation.value * tabWidthPx
                            translationX = if (isLtr) {
                                progressOffset
                            } else {
                                -progressOffset
                            }
                            scaleX = dampedDragAnimation.scaleX /
                                (1f - (dampedDragAnimation.velocity / 10f * 0.75f).coerceIn(-0.2f, 0.2f))
                            scaleY = dampedDragAnimation.scaleY *
                                (1f - (dampedDragAnimation.velocity / 10f * 0.25f).coerceIn(-0.2f, 0.2f))
                            if (!liquidGlassEnabled) {
                                scaleX = 1f
                                scaleY = 1f
                            }
                        }
                        .then(if (liquidGlassEnabled && interactionsEnabled) dampedDragAnimation.modifier else Modifier)
                        .then(
                            if (liquidGlassEnabled && backdrop != null && selectedIndicatorBackdrop != null) {
                                Modifier.drawBackdrop(
                                    backdrop = rememberCombinedBackdrop(backdrop, selectedIndicatorBackdrop),
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
                                    onDrawSurface = {
                                        val progress = dampedDragAnimation.pressProgress
                                        drawRect(
                                            color = if (isLight) {
                                                Color.Black.copy(alpha = 0.08f)
                                            } else {
                                                Color.White.copy(alpha = 0.10f)
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
                                            Color.Black.copy(alpha = 0.08f)
                                        } else {
                                            Color.White.copy(alpha = 0.11f)
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
private fun ApplyAppSystemBars(colorScheme: ColorScheme) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val darkTheme = colorScheme.background.luminance() < 0.5f
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

@Composable
private fun AppIdentityHeader(state: SonyHeadphoneUiState) {
    SectionCard(title = "SonyRebuild", icon = Icons.Rounded.Headphones) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.connectedProfile?.modelName ?: "Sony headphone control",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Status: ${if (state.connectedDevice != null) "connected" else state.scanState.lowercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: SonyHeadphoneUiState,
    bottomInnerPadding: androidx.compose.ui.unit.Dp,
    onOpenDevice: () -> Unit,
    onRefresh: () -> Unit,
) {
    PageColumn(bottomInnerPadding = bottomInnerPadding) {
        AppIdentityHeader(state = state)
        SectionCard(title = "Work status", icon = Icons.Rounded.Code) {
            val connected = state.connectedDevice != null
            InfoLine("Connection", if (connected) "Connected" else state.scanState)
            InfoLine("Protocol", if (state.deviceInfo.protocolReady) "Ready" else "Waiting")
            InfoLine("Profile", state.connectedProfile?.let { "${it.brand} ${it.modelName}" } ?: "No active profile")
            InfoLine("Features", "${state.supportedFeatures.count { it.implemented }} wired / ${state.supportedFeatures.size} tracked")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOpenDevice) {
                    Text(if (connected) "Open controls" else "Open devices")
                }
                OutlinedButton(onClick = onRefresh, enabled = connected) {
                    Text("Refresh")
                }
            }
        }
        SectionCard(title = "Current device", icon = Icons.Rounded.Bluetooth) {
            val connected = state.connectedDevice
            if (connected == null) {
                EmptyHint("No headset connected. Device page shows known devices and scan results.")
            } else {
                DeviceModelImage(
                    imageUrl = state.deviceInfo.modelImageUrl,
                    modelName = state.deviceInfo.modelName ?: connected.name,
                )
                InfoLine("Name", connected.name)
                InfoLine("Address", connected.address)
                InfoLine("Transport", state.connectedProfile?.transport?.name ?: "Unknown")
                val battery = state.batteryState.single ?: state.batteryState.left ?: state.batteryState.right
                InfoLine("Battery", battery?.let { "$it%" } ?: "Unknown")
            }
        }
        FeatureStatusCard(state.supportedFeatures)
    }
}

@Composable
private fun DeviceScreen(
    state: SonyHeadphoneUiState,
    bottomInnerPadding: androidx.compose.ui.unit.Dp,
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
) {
    PageColumn(bottomInnerPadding = bottomInnerPadding) {
        PageHeader(
            title = "Device",
            subtitle = if (state.connectedDevice == null) {
                "Connect a Sony control endpoint or inspect discovered devices"
            } else {
                "Headphone controls are shown from the active capability profile"
            },
        )
        ConnectionCard(
            state = state,
            onStartScan = onStartScan,
            onStopScan = onStopScan,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
            onRefresh = onRefresh,
        )
        EndpointDiagnosticsCard(state)
        if (state.connectedDevice != null) {
            DeviceInfoCard(state)
            BatteryCard(state)
            QuickControlCard(
                state = state,
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
    }
}

@Composable
private fun SettingsScreen(
    state: SonyHeadphoneUiState,
    bottomInnerPadding: Dp,
    renderCapabilities: UiRenderCapabilities,
    themeStyle: ThemeStyle,
    routeStack: List<SettingsRoute>,
    quickTarget: SettingsRoute?,
    onQuickTargetConsumed: () -> Unit,
    onRouteStackChanged: (List<SettingsRoute>) -> Unit,
    onOverlayModeChanged: (Boolean) -> Unit,
    onNavigationBarModeChanged: (ReareyeNavigationBarMode) -> Unit,
    onEffectsEnabledChanged: (Boolean) -> Unit,
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
            delay(NAV_BAR_EXIT_DURATION_MS)
            onRouteStackChanged(listOf(SettingsRoute.Root, route))
        }
    }

    fun closeRoute() {
        routeScope.launch {
            onRouteStackChanged(listOf(SettingsRoute.Root))
            delay(OVERLAY_ROUTE_EXIT_DURATION_MS)
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
                onOpenRoute = ::openRoute,
            )
            SettingsRoute.Appearance -> SettingsAppearanceScreen(
                bottomInnerPadding = bottomInnerPadding,
                renderCapabilities = renderCapabilities,
                themeStyle = themeStyle,
                onBack = ::closeRoute,
                onNavigationBarModeChanged = onNavigationBarModeChanged,
                onEffectsEnabledChanged = onEffectsEnabledChanged,
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
private fun SettingsRootScreen(
    state: SonyHeadphoneUiState,
    bottomInnerPadding: Dp,
    renderCapabilities: UiRenderCapabilities,
    themeStyle: ThemeStyle,
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
            } / ${themeStyle.title}",
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
private fun SettingsAppearanceScreen(
    bottomInnerPadding: Dp,
    renderCapabilities: UiRenderCapabilities,
    themeStyle: ThemeStyle,
    onBack: () -> Unit,
    onNavigationBarModeChanged: (ReareyeNavigationBarMode) -> Unit,
    onEffectsEnabledChanged: (Boolean) -> Unit,
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
private fun SettingsProtocolScreen(
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
private fun SettingsDiagnosticsScreen(
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

@Composable
private fun SettingsModulesScreen(
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
private fun RouteHeader(route: SettingsRoute, onBack: () -> Unit) {
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
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = route.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = route.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutScreen(
    state: SonyHeadphoneUiState,
    bottomInnerPadding: Dp,
    themeStyle: ThemeStyle,
    renderCapabilities: UiRenderCapabilities,
) {
    val context = LocalContext.current
    var route by remember { mutableStateOf(AboutRoute.Root) }
    BackHandler(enabled = route != AboutRoute.Root) {
        route = AboutRoute.Root
    }
    AnimatedContent(
        targetState = AboutAnimatedRoute(route, if (route == AboutRoute.Root) 0 else 1),
        contentKey = { it.route.name },
        transitionSpec = {
            reareyeHorizontalTransform(forward = targetState.depth >= initialState.depth)
        },
        label = "AboutRouteTransition",
        modifier = Modifier.fillMaxSize(),
    ) { animatedRoute ->
        when (animatedRoute.route) {
            AboutRoute.Root -> AboutRootScreen(
                state = state,
                context = context,
                bottomInnerPadding = bottomInnerPadding,
                onOpenProtocol = { route = AboutRoute.Protocol },
            )
            AboutRoute.Protocol -> AboutDetailScreen(
                route = AboutRoute.Protocol,
                bottomInnerPadding = bottomInnerPadding,
                onBack = { route = AboutRoute.Root },
            ) {
                SectionCard(title = "Protocol references") {
                    InfoLine("GATT services", "export/01-ble-gatt-layer")
                    InfoLine("Tandem V2 commands", "export/02-tandem-protocol-v2")
                    InfoLine("Tandem V1 commands", "export/03-tandem-protocol-v1")
                    InfoLine("Feature bridge notes", "export/04-app-layer")
                    InfoLine("Protocol documentation", "export/05-full-protocol-documentation.md")
                }
                SectionCard(title = "Safety note") {
                    Text(
                        text = "Only simple local BLE controls are wired in this version. Firmware update, calibration, and irreversible device operations remain placeholders until they can be tested carefully.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutRootScreen(
    state: SonyHeadphoneUiState,
    context: Context,
    bottomInnerPadding: Dp,
    onOpenProtocol: () -> Unit,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val renderCapabilities = LocalUiRenderCapabilities.current
    val renderEffectsSupported = remember { isRenderEffectSupported() }
    val acrylicEnabled = renderCapabilities.effectsEnabled && renderEffectsSupported
    val hazeState = rememberAboutHazeState()
    val hazeStyle = rememberAboutHazeStyle()
    val visualTokens = rememberGlassVisualTokens()
    val scrollProgress by remember(listState, density) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                val distancePx = with(density) { 389.dp.toPx() }
                (listState.firstVisibleItemScrollOffset / distancePx).coerceIn(0f, 1f)
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        val backdrop = LocalTextureBackdrop.current
        AboutGradientBackground(progress = scrollProgress)
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .aboutAcrylicSource(hazeState, enabled = acrylicEnabled),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 18.dp,
                end = 16.dp,
                bottom = bottomInnerPadding + 12.dp,
            ),
        ) {
            item {
                AboutHero(
                    state = state,
                    versionName = appVersionName(context),
                    scrollProgress = scrollProgress,
                    backdrop = backdrop,
                    visualTokens = visualTokens,
                )
            }
            item {
                SectionCard(title = "App information") {
                    InfoLine("Name", "SonyRebuild")
                    InfoLine("Package", context.packageName)
                    InfoLine("Version", appVersionName(context))
                    InfoLine("Build target", "Local Bluetooth controller")
                    Text(
                        text = "A clean-room Android controller for Sony Bluetooth headphones. It keeps cloud, account, firmware distribution, and official package identity outside this app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SectionCard(title = "Connected device") {
                    DeviceModelImage(
                        imageUrl = state.deviceInfo.modelImageUrl,
                        modelName = state.deviceInfo.modelName ?: state.connectedDevice?.name,
                    )
                    InfoLine("Model", state.deviceInfo.modelName ?: state.connectedDevice?.name ?: "Not connected")
                    InfoLine("Firmware", state.deviceInfo.firmwareVersion ?: "Unknown")
                    InfoLine("Series / color", state.deviceInfo.seriesAndColor ?: "Unknown")
                    InfoLine("Profile", state.connectedProfile?.let { "${it.adapterId} / ${it.protocolName}" } ?: "No active profile")
                    InfoLine("Transport", state.connectedProfile?.transport?.name ?: "Unknown")
                }
            }
            item {
                ManagerCard(
                    title = AboutRoute.Protocol.title,
                    summary = AboutRoute.Protocol.subtitle,
                    icon = Icons.Rounded.Code,
                    onClick = onOpenProtocol,
                )
            }
        }
        AboutTopTitle(
            progress = scrollProgress,
            hazeState = hazeState,
            hazeStyle = hazeStyle,
            enabled = acrylicEnabled,
        )
    }
}

@Composable
private fun AboutDetailScreen(
    route: AboutRoute,
    bottomInnerPadding: Dp,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    PageColumn(bottomInnerPadding = bottomInnerPadding) {
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
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = route.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = route.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content()
    }
}

@Composable
private fun BoxScope.AboutTopTitle(
    progress: Float,
    hazeState: dev.chrisbanes.haze.HazeState,
    hazeStyle: dev.chrisbanes.haze.HazeStyle,
    enabled: Boolean,
) {
    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f * progress)
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(58.dp)
            .alpha(progress)
            .aboutAcrylicEffect(hazeState, hazeStyle, enabled = enabled)
            .background(containerColor)
            .padding(horizontal = 18.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Headphones,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "SonyRebuild",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = progress),
        )
    }
}

@Composable
private fun AboutHero(
    state: SonyHeadphoneUiState,
    versionName: String,
    scrollProgress: Float,
    backdrop: TextureLayerBackdrop?,
    visualTokens: GlassVisualTokens,
) {
    val renderCapabilities = LocalUiRenderCapabilities.current
    val versionFade = (scrollProgress / 0.45f).coerceIn(0f, 1f)
    val titleFade = ((scrollProgress - 0.16f) / 0.48f).coerceIn(0f, 1f)
    val iconFade = ((scrollProgress - 0.32f) / 0.50f).coerceIn(0f, 1f)
    val glassEnabled = renderCapabilities.glassCardsEnabled && backdrop != null
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 30.dp, bottom = 22.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .graphicsLayer {
                    alpha = 1f - iconFade
                    scaleX = 1f - iconFade * 0.05f
                    scaleY = 1f - iconFade * 0.05f
                }
                .size(86.dp)
                .then(
                    if (glassEnabled) {
                        Modifier.textureBlur(
                            backdrop = backdrop,
                            shape = RoundedCornerShape(8.dp),
                            blurRadius = 150f,
                            noiseCoefficient = 0.001f,
                            colors = BlurColors(blendColors = visualTokens.logoBlendColors),
                            enabled = true,
                        )
                    } else {
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f))
                    }
                ),
        ) {
            Icon(
                imageVector = Icons.Rounded.Headphones,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
        }
        Text(
            text = state.connectedProfile?.modelName ?: "SonyRebuild",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.graphicsLayer {
                alpha = 1f - titleFade
                scaleX = 1f - titleFade * 0.05f
                scaleY = 1f - titleFade * 0.05f
            },
        )
        Text(
            text = "Version $versionName",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer {
                alpha = 1f - versionFade
                scaleX = 1f - versionFade * 0.05f
                scaleY = 1f - versionFade * 0.05f
            },
        )
    }
}

@Composable
private fun AboutGradientBackground(progress: Float) {
    if (!LocalUiRenderCapabilities.current.backgroundGradientEnabled) {
        return
    }
    val alpha = 1f - progress
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.0f),
                    ),
                    start = Offset.Zero,
                    end = Offset(900f, 1200f),
                )
            )
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.34f),
                        Color.Transparent,
                    ),
                    center = Offset(160f, 180f),
                    radius = 620f,
                )
            )
    )
}

@Composable
private fun ConnectionCard(
    state: SonyHeadphoneUiState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (DiscoveredSonyDevice) -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
) {
    SectionCard(title = "Connection", icon = Icons.Rounded.Bluetooth) {
        val connected = state.connectedDevice
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            StatusPill(state.scanState, connected != null || state.isScanning)
            state.connectionInfo?.let {
                StatusPill("MTU ${it.mtu}", true)
            }
            state.permissionIssue?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        if (connected == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onStartScan, enabled = !state.isScanning) {
                    Text("Scan")
                }
                OutlinedButton(onClick = onStopScan, enabled = state.isScanning) {
                    Text("Stop")
                }
            }
            if (state.knownDevices.isNotEmpty()) {
                Text(
                    text = "Known devices",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                state.knownDevices.forEach { device ->
                    DeviceRow(device = device, onConnect = { onConnect(device) })
                }
            }
            Text(
                text = "Scan results",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.discoveredDevices.isEmpty()) {
                EmptyHint("No Sony Tandem V2 devices found yet.")
            } else {
                state.discoveredDevices.forEach { device ->
                    DeviceRow(device = device, onConnect = { onConnect(device) })
                }
            }
        } else {
            InfoLine("Connected", "${connected.name} (${connected.address})")
            state.connectedProfile?.let { profile ->
                InfoLine("Profile", "${profile.brand} ${profile.modelName}")
                InfoLine("Transport", profile.transport.name)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onRefresh) {
                    Text("Refresh")
                }
                OutlinedButton(onClick = onDisconnect) {
                    Text("Disconnect")
                }
            }
        }
    }
}

@Composable
private fun EndpointDiagnosticsCard(state: SonyHeadphoneUiState) {
    val diagnostic = state.endpointDiagnostic ?: return
    SectionCard(title = "Endpoint diagnostics", icon = Icons.Rounded.Info) {
        Text(
            text = diagnostic.reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        InfoLine("Mode", "LE Audio / auxiliary GATT endpoint")
        InfoLine(
            "LEA compatibility",
            diagnostic.leAudioSwitchCompatibility?.toString() ?: "Unknown",
        )
        diagnostic.friendlyName?.let { InfoLine("Friendly name", it) }
        diagnostic.publicAddress?.let { InfoLine("Public address", it) }
        InfoLine("Services", diagnostic.serviceLabels.joinToString())
        diagnostic.rawReads.entries.take(5).forEach { (name, value) ->
            InfoLine(name, value)
        }
    }
}

@Composable
private fun DeviceInfoCard(state: SonyHeadphoneUiState) {
    SectionCard(title = "Device info") {
        val info = state.deviceInfo
        DeviceModelImage(
            imageUrl = info.modelImageUrl,
            modelName = info.modelName ?: state.connectedDevice?.name,
        )
        InfoLine("Protocol channel", if (info.protocolReady) "Sony Tandem ready" else "Not ready")
        state.connectedProfile?.let { profile ->
            InfoLine("Adapter", "${profile.adapterId} / ${profile.protocolName}")
            InfoLine("Transport", profile.transport.name)
        }
        InfoLine("Model", info.modelName ?: state.connectedDevice?.name ?: "Unknown")
        InfoLine("Firmware", info.firmwareVersion ?: "Unknown")
        InfoLine("Series / color", info.seriesAndColor ?: "Unknown")
        InfoLine("Image match", info.modelImageUrl?.let { info.modelColor ?: "Default" } ?: "Default placeholder")
    }
}

@Composable
private fun DeviceModelImage(
    imageUrl: String?,
    modelName: String?,
) {
    val bitmap by produceState<Bitmap?>(initialValue = imageUrl?.let(::cachedBitmap), imageUrl) {
        value = imageUrl?.let { cachedBitmap(it) ?: loadRemoteBitmap(it) }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = modelName ?: "Sony device",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .padding(12.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Bluetooth,
                contentDescription = modelName ?: "Sony device",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

@Composable
private fun BatteryCard(state: SonyHeadphoneUiState) {
    SectionCard(title = "Battery", icon = Icons.Rounded.BatteryChargingFull) {
        val battery = state.batteryState
        val headsetBatteryOnly = state.connectedProfile?.capabilities?.formFactor == HeadphoneFormFactor.HEADSET
        if (headsetBatteryOnly) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                BatteryTile("Headset", battery.single ?: battery.left ?: battery.right)
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                BatteryTile("Left", battery.left)
                BatteryTile("Right", battery.right)
                BatteryTile("Case", battery.cradle)
            }
        }
        if (battery.raw.isNotEmpty()) {
            Text(
                text = "Raw battery payload: ${battery.raw}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun QuickControlCard(
    state: SonyHeadphoneUiState,
    onSetNoiseControlMode: (NoiseControlMode) -> Unit,
    onSetAmbientLevel: (Int) -> Unit,
    onSetAmbientVoiceMode: (Boolean) -> Unit,
    onSetEqPreset: (EqPresetId) -> Unit,
    onSetClearBass: (Int) -> Unit,
    onSetCustomEqBand: (Int, Int) -> Unit,
    onPlaybackPrevious: () -> Unit,
    onPlaybackPlayPause: () -> Unit,
    onPlaybackNext: () -> Unit,
) {
    SectionCard(title = "Quick controls", icon = Icons.Rounded.MusicNote) {
        var showEqDetails by remember { mutableStateOf(false) }
        val connected = state.connectedDevice != null && state.deviceInfo.protocolReady
        val noiseEnabled = connected && state.connectedProfile.supports(HeadphoneFeature.NOISE_CONTROL)
        val ambientLevelEnabled = connected && state.connectedProfile.supports(HeadphoneFeature.AMBIENT_LEVEL)
        val ambientVoiceEnabled = connected && state.connectedProfile.supports(HeadphoneFeature.AMBIENT_VOICE_MODE)
        val eqEnabled = connected && state.connectedProfile.supports(HeadphoneFeature.EQ)
        val playbackEnabled = connected && state.connectedProfile.supports(HeadphoneFeature.PLAYBACK_CONTROL)
        val controlMode = state.noiseControlState.controlMode ?: when {
            state.noiseControlState.ambientSoundEnabled == true -> NoiseControlMode.AMBIENT_SOUND
            state.noiseControlState.noiseCancellingEnabled == true -> NoiseControlMode.NOISE_CANCELLING
            state.noiseControlState.noiseCancellingEnabled == false ||
                state.noiseControlState.ambientSoundEnabled == false -> NoiseControlMode.OFF
            else -> null
        }
        val ambientLevel = (state.noiseControlState.ambientLevel ?: 10).coerceIn(1, 20)
        NoiseControlModeCard(
            selectedMode = controlMode,
            ambientLevel = ambientLevel,
            voiceFocus = state.noiseControlState.ambientVoiceMode,
            enabled = noiseEnabled,
            ambientLevelEnabled = ambientLevelEnabled,
            ambientVoiceEnabled = ambientVoiceEnabled,
            onSetMode = onSetNoiseControlMode,
            onSetAmbientLevel = onSetAmbientLevel,
            onSetAmbientVoiceMode = onSetAmbientVoiceMode,
        )
        EqControlCard(
            selectedPreset = state.eqState.preset,
            clearBass = state.eqState.clearBass ?: 0,
            bandSteps = state.eqState.bandSteps,
            bandStepCenter = state.eqState.bandStepCenter,
            usesCustomEqPayload = state.eqState.usesCustomEqPayload,
            expanded = showEqDetails,
            enabled = eqEnabled,
            onToggleExpanded = { showEqDetails = !showEqDetails },
            onSetEqPreset = onSetEqPreset,
            onSetClearBass = onSetClearBass,
            onSetCustomEqBand = onSetCustomEqBand,
        )
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val playPauseIcon = if (state.playbackStatus == PlaybackStatus.PLAYING) {
                Icons.Rounded.Pause
            } else {
                Icons.Rounded.PlayArrow
            }
            PlaybackButton(Icons.Rounded.SkipPrevious, "Previous", playbackEnabled, onPlaybackPrevious)
            PlaybackButton(playPauseIcon, "Play or pause", playbackEnabled, onPlaybackPlayPause)
            PlaybackButton(Icons.Rounded.SkipNext, "Next", playbackEnabled, onPlaybackNext)
        }
        Text(
            text = "Playback: ${state.playbackStatus.name.lowercase()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EqControlCard(
    selectedPreset: EqPresetId?,
    clearBass: Int,
    bandSteps: List<Int>,
    bandStepCenter: Int,
    usesCustomEqPayload: Boolean,
    expanded: Boolean,
    enabled: Boolean,
    onToggleExpanded: () -> Unit,
    onSetEqPreset: (EqPresetId) -> Unit,
    onSetClearBass: (Int) -> Unit,
    onSetCustomEqBand: (Int, Int) -> Unit,
) {
    val bandLabels = listOf("400 Hz", "1 kHz", "2.5 kHz", "6.3 kHz", "16 kHz")
    val presets = listOf(
        EqPresetId.OFF,
        EqPresetId.BRIGHT,
        EqPresetId.EXCITED,
        EqPresetId.MELLOW,
        EqPresetId.RELAXED,
        EqPresetId.VOCAL,
        EqPresetId.TREBLE,
        EqPresetId.BASS,
        EqPresetId.SPEECH,
        EqPresetId.CUSTOM,
        EqPresetId.USER_SETTING1,
        EqPresetId.USER_SETTING2,
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Text(
                text = "EQ / Clear Bass",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Preset: ${selectedPreset?.displayName ?: "Unknown"}  Bands: ${bandSteps.size}  Center: $bandStepCenter",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingRow(
                title = "Equalizer",
                subtitle = "Clear Bass ${clearBass.coerceIn(-10, 10)}",
                trailing = {
                    IconButton(onClick = onToggleExpanded, enabled = enabled) {
                        Icon(
                            imageVector = if (expanded) {
                                Icons.Rounded.KeyboardArrowUp
                            } else {
                                Icons.Rounded.KeyboardArrowDown
                            },
                            contentDescription = if (expanded) "Collapse equalizer" else "Expand equalizer",
                        )
                    }
                },
            )
            if (!expanded) {
                return@Column
            }
            presets.chunked(3).forEach { rowPresets ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    rowPresets.forEach { preset ->
                        ModeButton(
                            text = preset.displayName,
                            selected = selectedPreset == preset,
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                            onClick = { onSetEqPreset(preset) },
                        )
                    }
                    repeat(3 - rowPresets.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            SettingRow(
                title = "Clear Bass",
                subtitle = "Level ${clearBass.coerceIn(-10, 10)}",
                trailing = {
                    StepperControl(
                        value = clearBass.coerceIn(-10, 10),
                        enabled = enabled,
                        min = -10,
                        max = 10,
                        onDecrease = { onSetClearBass(clearBass - 1) },
                        onIncrease = { onSetClearBass(clearBass + 1) },
                    )
                },
            )
            var sliderBass by remember(clearBass) { mutableFloatStateOf(clearBass.coerceIn(-10, 10).toFloat()) }
            Slider(
                value = sliderBass,
                onValueChange = { sliderBass = it },
                onValueChangeFinished = { onSetClearBass(sliderBass.toInt().coerceIn(-10, 10)) },
                enabled = enabled,
                valueRange = -10f..10f,
                steps = 19,
            )
            if (bandSteps.isEmpty()) {
                Text(
                    text = "No editable EQ band payload reported by this headset.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = if (usesCustomEqPayload) "CUSTOM EQ payload active" else "Preset EQ band payload active",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                bandSteps.forEachIndexed { index, step ->
                    val bandLabel = bandLabels.getOrNull(index) ?: "Band ${index + 1}"
                    SettingRow(
                        title = bandLabel,
                        subtitle = "Level ${step.coerceIn(-10, 10)}",
                        trailing = {
                            StepperControl(
                                value = step.coerceIn(-10, 10),
                                enabled = enabled,
                                min = -10,
                                max = 10,
                                onDecrease = { onSetCustomEqBand(index, step - 1) },
                                onIncrease = { onSetCustomEqBand(index, step + 1) },
                            )
                        },
                    )
                    var sliderBand by remember(index, step) { mutableFloatStateOf(step.coerceIn(-10, 10).toFloat()) }
                    Slider(
                        value = sliderBand,
                        onValueChange = { sliderBand = it },
                        onValueChangeFinished = {
                            onSetCustomEqBand(index, sliderBand.toInt().coerceIn(-10, 10))
                        },
                        enabled = enabled,
                        valueRange = -10f..10f,
                        steps = 19,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoiseControlModeCard(
    selectedMode: NoiseControlMode?,
    ambientLevel: Int,
    voiceFocus: Boolean,
    enabled: Boolean,
    ambientLevelEnabled: Boolean,
    ambientVoiceEnabled: Boolean,
    onSetMode: (NoiseControlMode) -> Unit,
    onSetAmbientLevel: (Int) -> Unit,
    onSetAmbientVoiceMode: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Text(
                text = "Noise / Ambient",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ModeButton(
                    text = "降噪",
                    selected = selectedMode == NoiseControlMode.NOISE_CANCELLING,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onSetMode(NoiseControlMode.NOISE_CANCELLING) },
                )
                ModeButton(
                    text = "环境声",
                    selected = selectedMode == NoiseControlMode.AMBIENT_SOUND,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onSetMode(NoiseControlMode.AMBIENT_SOUND) },
                )
                ModeButton(
                    text = "关闭",
                    selected = selectedMode == NoiseControlMode.OFF,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onSetMode(NoiseControlMode.OFF) },
                )
            }
            if (selectedMode == NoiseControlMode.AMBIENT_SOUND) {
                SettingRow(
                    title = "环境声强度",
                    subtitle = "Level $ambientLevel / 20",
                    trailing = {
                        StepperControl(
                            value = ambientLevel,
                            enabled = enabled && ambientLevelEnabled,
                            onDecrease = { onSetAmbientLevel(ambientLevel - 1) },
                            onIncrease = { onSetAmbientLevel(ambientLevel + 1) },
                        )
                    },
                )
                var sliderLevel by remember(ambientLevel) { mutableFloatStateOf(ambientLevel.toFloat()) }
                Slider(
                    value = sliderLevel,
                    onValueChange = { sliderLevel = it },
                    onValueChangeFinished = { onSetAmbientLevel(sliderLevel.toInt().coerceIn(1, 20)) },
                    enabled = enabled && ambientLevelEnabled,
                    valueRange = 1f..20f,
                    steps = 18,
                )
                SettingRow(
                    title = "关注语音",
                    subtitle = if (voiceFocus) "Voice focus enabled" else "Normal ambient sound",
                    trailing = {
                        Switch(
                            checked = voiceFocus,
                            enabled = enabled && ambientVoiceEnabled,
                            onCheckedChange = onSetAmbientVoiceMode,
                        )
                    },
                )
            }
            if (selectedMode == null) {
                Text(
                    text = "Current NC/ASM mode is not reported by this headset; controls are still available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ModeButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier) {
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FeatureStatusCard(features: List<FeatureStatus>) {
    SectionCard(title = "Feature map", icon = Icons.Rounded.Code) {
        features.forEach { feature ->
            SettingRow(
                title = feature.title,
                subtitle = feature.description,
                trailing = { StatusPill(if (feature.implemented) "wired" else "reserved", feature.implemented) },
            )
        }
    }
}

private fun ConnectedHeadphoneProfile?.supports(feature: HeadphoneFeature): Boolean =
    this?.supports(feature) == true

@Composable
private fun PageColumn(
    bottomInnerPadding: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 18.dp,
            end = 16.dp,
            bottom = bottomInnerPadding + 12.dp,
        ),
    ) {
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        }
    }
}

private fun reareyeHorizontalTransform(forward: Boolean): ContentTransform =
    fadeIn(
        animationSpec = tween(
            durationMillis = 210,
            delayMillis = 50,
            easing = LinearOutSlowInEasing,
        )
    ) + slideInHorizontally(
        animationSpec = tween(
            durationMillis = 280,
            easing = FastOutSlowInEasing,
        )
    ) { fullWidth ->
        if (forward) fullWidth / 9 else -fullWidth / 9
    } togetherWith fadeOut(
        animationSpec = tween(
            durationMillis = 110,
            easing = FastOutLinearInEasing,
        )
    ) + slideOutHorizontally(
        animationSpec = tween(
            durationMillis = 190,
            easing = FastOutLinearInEasing,
        )
    ) { fullWidth ->
        if (forward) -fullWidth / 12 else fullWidth / 12
    }

@Composable
private fun PageHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            content()
        }
    }
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = RoundedCornerShape(8.dp),
    content: @Composable () -> Unit,
) {
    val backdrop = LocalTextureBackdrop.current
    val renderCapabilities = LocalUiRenderCapabilities.current
    val glassEnabled = renderCapabilities.glassCardsEnabled && backdrop != null
    val isLight = MaterialTheme.colorScheme.background.luminance() >= 0.5f
    val fallbackContainerColor = MaterialTheme.colorScheme.surface.copy(
        alpha = if (isLight) 0.94f else 0.98f,
    )
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = if (glassEnabled) 0.16f else 0.14f)
    val blurColors = BlurColors(
        blendColors = rememberGlassVisualTokens().cardBlendColors,
    )
    Card(
        shape = shape,
        border = if (glassEnabled) null else BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = modifier.then(
            if (glassEnabled) {
                Modifier.textureBlur(
                    backdrop = backdrop,
                    shape = shape,
                    blurRadius = 60f,
                    noiseCoefficient = 0.001f,
                    colors = blurColors,
                    enabled = true,
                )
            } else {
                Modifier
                    .shadow(
                        elevation = 1.dp,
                        shape = shape,
                        ambientColor = Color.Black.copy(alpha = if (isLight) 0.06f else 0.20f),
                        spotColor = Color.Black.copy(alpha = if (isLight) 0.08f else 0.22f),
                    )
                    .clip(shape)
                    .background(fallbackContainerColor)
            }
        ),
    ) {
        content()
    }
}

@Composable
private fun ManagerCard(
    title: String,
    summary: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(device: DiscoveredSonyDevice, onConnect: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = device.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${device.address}  ${device.source}  RSSI ${device.rssi}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (device.isLikelyControlEndpoint) {
                        "BLE control candidate"
                    } else if (device.sonyAd != null) {
                        "Sony AD found; official app uses SPP unless LE control flag is set"
                    } else {
                        "Classic audio endpoint; Connect uses official Sony SPP UUID"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                device.sonyAd?.let { ad ->
                    Text(
                        text = ad.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ad.androidGattCapable || ad.leGattControlFlag) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TextButton(onClick = onConnect) {
                Text("Connect")
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BatteryTile(
    label: String,
    value: Int?,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.weight(1f),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
        ) {
            Text(
                text = value?.let { "$it%" } ?: "--",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepperControl(
    value: Int,
    enabled: Boolean,
    min: Int = 0,
    max: Int = 20,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onDecrease, enabled = enabled && value > min) {
            Text("-")
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        TextButton(onClick = onIncrease, enabled = enabled && value < max) {
            Text("+")
        }
    }
}

@Composable
private fun PlaybackButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun <T> SegmentedChoice(
    selected: T,
    values: Iterable<T>,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        maxItemsInEachRow = 2,
        modifier = Modifier.widthIn(max = 220.dp),
    ) {
        values.forEach { value ->
            ModeButton(
                text = label(value),
                selected = selected == value,
                enabled = true,
                modifier = Modifier.heightIn(min = 36.dp),
                onClick = { onSelected(value) },
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.38f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.62f),
        )
    }
}

@Composable
private fun StatusPill(text: String, active: Boolean) {
    AssistChip(
        onClick = {},
        label = { Text(text) },
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        ),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun appVersionName(context: Context): String =
    runCatching {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName ?: "Unknown"
    }.getOrDefault("Unknown")

private val DeviceImageCache = LruCache<String, Bitmap>(16)

private fun cachedBitmap(imageUrl: String): Bitmap? = synchronized(DeviceImageCache) {
    DeviceImageCache.get(imageUrl)
}

private suspend fun loadRemoteBitmap(imageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
    cachedBitmap(imageUrl)?.let { return@withContext it }
    runCatching {
        val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 8_000
            instanceFollowRedirects = true
        }
        try {
            connection.inputStream.use(BitmapFactory::decodeStream)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()?.also { bitmap ->
        synchronized(DeviceImageCache) {
            DeviceImageCache.put(imageUrl, bitmap)
        }
    }
}
