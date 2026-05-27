package dev.ignotus.openbuds.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

internal data class UiRenderCapabilities(
    val mode: ReareyeNavigationBarMode,
    val tier: EffectsTier,
) {
    val effectsEnabled: Boolean = tier != EffectsTier.DISABLED

    val floatingBottomBarEnabled: Boolean = mode == ReareyeNavigationBarMode.Floating ||
        mode == ReareyeNavigationBarMode.FloatingGlass

    val semiTransparentBottomBar: Boolean = mode == ReareyeNavigationBarMode.SemiTransparent

    val rootBackdropEnabled: Boolean = effectsEnabled &&
        mode == ReareyeNavigationBarMode.FloatingGlass &&
        tier >= EffectsTier.LIGHT_GLASS

    val navigationBackdropEnabled: Boolean = effectsEnabled &&
        mode == ReareyeNavigationBarMode.FloatingGlass &&
        tier >= EffectsTier.BLUR_ONLY

    val liquidGlassEnabled: Boolean = tier == EffectsTier.FULL_GLASS &&
        mode == ReareyeNavigationBarMode.FloatingGlass

    val vibrancyEnabled: Boolean = tier == EffectsTier.FULL_GLASS

    val glassCardsEnabled: Boolean = effectsEnabled &&
        tier >= EffectsTier.BLUR_ONLY

    val backgroundGradientEnabled: Boolean = effectsEnabled
}

@Composable
internal fun rememberUiRenderCapabilities(
    mode: ReareyeNavigationBarMode,
    tier: EffectsTier,
): UiRenderCapabilities {
    return remember(mode, tier) {
        UiRenderCapabilities(
            mode = mode,
            tier = tier,
        )
    }
}
