package dev.ignotus.openbuds.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

internal data class UiRenderCapabilities(
    val mode: ReareyeNavigationBarMode,
    val userEffectsEnabled: Boolean,
) {
    val effectsEnabled: Boolean = userEffectsEnabled
    val floatingBottomBarEnabled: Boolean = mode == ReareyeNavigationBarMode.Floating ||
        mode == ReareyeNavigationBarMode.FloatingGlass
    val semiTransparentBottomBar: Boolean = mode == ReareyeNavigationBarMode.SemiTransparent
    val rootBackdropEnabled: Boolean = effectsEnabled &&
        mode == ReareyeNavigationBarMode.FloatingGlass
    val navigationBackdropEnabled: Boolean = effectsEnabled &&
        mode == ReareyeNavigationBarMode.FloatingGlass
    val liquidGlassEnabled: Boolean = navigationBackdropEnabled
    val glassCardsEnabled: Boolean = rootBackdropEnabled
    val backgroundGradientEnabled: Boolean = effectsEnabled
}

@Composable
internal fun rememberUiRenderCapabilities(
    mode: ReareyeNavigationBarMode,
    userEffectsEnabled: Boolean,
): UiRenderCapabilities {
    return remember(mode, userEffectsEnabled) {
        UiRenderCapabilities(
            mode = mode,
            userEffectsEnabled = userEffectsEnabled,
        )
    }
}
