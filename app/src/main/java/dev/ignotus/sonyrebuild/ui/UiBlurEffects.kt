package dev.ignotus.sonyrebuild.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur

@Immutable
internal data class GlassVisualTokens(
    val cardBlendColors: List<BlendColorEntry>,
    val logoBlendColors: List<BlendColorEntry>,
)

@Composable
internal fun rememberGlassVisualTokens(): GlassVisualTokens {
    val surface = MaterialTheme.colorScheme.surface
    val isDarkTheme = surface.luminance() < 0.5f

    return remember(surface, isDarkTheme) {
        GlassVisualTokens(
            cardBlendColors = glassCardBlendColors(isDarkTheme),
            logoBlendColors = glassLogoBlendColors(isDarkTheme),
        )
    }
}

@Composable
internal fun rememberTextureBackdrop(
    enabled: Boolean,
): LayerBackdrop? {
    if (!enabled || !isRenderEffectSupported()) return null
    return rememberLayerBackdrop()
}

internal fun Modifier.textureBackdropSource(
    backdrop: LayerBackdrop?,
): Modifier {
    return if (backdrop != null) {
        this.layerBackdrop(backdrop)
    } else {
        this
    }
}

@Composable
internal fun rememberAboutHazeState(): HazeState {
    return remember { HazeState() }
}

@Composable
internal fun rememberAboutHazeStyle(): HazeStyle {
    val surface = MaterialTheme.colorScheme.surface
    val tintAlpha = if (surface.luminance() < 0.5f) 0.72f else 0.82f

    return remember(surface, tintAlpha) {
        HazeStyle(
            backgroundColor = surface,
            tint = HazeTint(surface.copy(alpha = tintAlpha)),
        )
    }
}

@OptIn(ExperimentalHazeApi::class)
internal fun Modifier.aboutAcrylicEffect(
    hazeState: HazeState,
    hazeStyle: HazeStyle,
    enabled: Boolean = true,
    blurRadius: Dp = 24.dp,
): Modifier {
    if (!enabled || !isRenderEffectSupported()) return this

    return this.hazeEffect(
        state = hazeState,
        style = hazeStyle,
    ) {
        this.blurRadius = blurRadius
        inputScale = HazeInputScale.Fixed(0.35f)
        noiseFactor = 0f
        forceInvalidateOnPreDraw = false
    }
}

@OptIn(ExperimentalHazeApi::class)
internal fun Modifier.aboutAcrylicSource(
    hazeState: HazeState,
    enabled: Boolean = true,
): Modifier {
    if (!enabled || !isRenderEffectSupported()) return this

    return this.hazeSource(state = hazeState)
}

@Composable
internal fun BlurredBar(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.then(
            if (backdrop != null) {
                Modifier.textureBlur(
                    backdrop = backdrop,
                    shape = RectangleShape,
                    blurRadius = 25f * androidx.compose.ui.platform.LocalDensity.current.density,
                    colors = BlurColors(
                        blendColors = listOf(
                            BlendColorEntry(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.87f),
                            ),
                        ),
                    ),
                )
            } else {
                Modifier
            },
        ),
    ) {
        content()
    }
}

internal fun glassCardBlendColors(isDarkTheme: Boolean): List<BlendColorEntry> {
    return if (isDarkTheme) {
        listOf(
            BlendColorEntry(Color(0x4DA9A9A9), BlurBlendMode.Luminosity),
            BlendColorEntry(Color(0x1A9C9C9C), BlurBlendMode.PlusDarker),
        )
    } else {
        listOf(
            BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
            BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
        )
    }
}

internal fun glassLogoBlendColors(isDarkTheme: Boolean): List<BlendColorEntry> {
    return if (isDarkTheme) {
        listOf(
            BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
            BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
            BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
        )
    } else {
        listOf(
            BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
            BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
            BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
        )
    }
}
