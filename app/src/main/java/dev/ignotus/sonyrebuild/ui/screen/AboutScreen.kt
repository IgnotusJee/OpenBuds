package dev.ignotus.sonyrebuild.ui.screen

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop as TextureLayerBackdrop
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.textureBlur
import dev.ignotus.sonyrebuild.data.SonyHeadphoneUiState
import dev.ignotus.sonyrebuild.ui.AboutAnimatedRoute
import dev.ignotus.sonyrebuild.ui.AboutRoute
import dev.ignotus.sonyrebuild.ui.GlassCard
import dev.ignotus.sonyrebuild.ui.GlassVisualTokens
import dev.ignotus.sonyrebuild.ui.InfoLine
import dev.ignotus.sonyrebuild.ui.LocalTextureBackdrop
import dev.ignotus.sonyrebuild.ui.LocalUiRenderCapabilities
import dev.ignotus.sonyrebuild.ui.ManagerCard
import dev.ignotus.sonyrebuild.ui.PageColumn
import dev.ignotus.sonyrebuild.ui.SectionCard
import dev.ignotus.sonyrebuild.ui.ThemeStyle
import dev.ignotus.sonyrebuild.ui.UiRenderCapabilities
import dev.ignotus.sonyrebuild.ui.aboutAcrylicEffect
import dev.ignotus.sonyrebuild.ui.aboutAcrylicSource
import dev.ignotus.sonyrebuild.ui.appVersionName
import dev.ignotus.sonyrebuild.ui.rememberAboutHazeState
import dev.ignotus.sonyrebuild.ui.rememberAboutHazeStyle
import dev.ignotus.sonyrebuild.ui.rememberGlassVisualTokens
import dev.ignotus.sonyrebuild.ui.reareyeHorizontalTransform
import dev.ignotus.sonyrebuild.ui.screen.DeviceModelImage
import dev.ignotus.sonyrebuild.ui.aboutAcrylicSource
import dev.ignotus.sonyrebuild.ui.appVersionName
import dev.ignotus.sonyrebuild.ui.reareyeHorizontalTransform
import dev.ignotus.sonyrebuild.ui.rememberAboutHazeState
import dev.ignotus.sonyrebuild.ui.rememberAboutHazeStyle
import dev.ignotus.sonyrebuild.ui.rememberGlassVisualTokens

@Composable
internal fun AboutScreen(
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
internal fun AboutRootScreen(
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
internal fun AboutDetailScreen(
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
        content()
    }
}

@Composable
internal fun BoxScope.AboutTopTitle(
    progress: Float,
    hazeState: HazeState,
    hazeStyle: HazeStyle,
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
internal fun AboutHero(
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
internal fun AboutGradientBackground(progress: Float) {
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
