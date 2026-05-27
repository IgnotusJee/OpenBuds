package dev.ignotus.openbuds.ui

import android.content.Context
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported

enum class EffectsTier {
    DISABLED,
    BLUR_ONLY,
    LIGHT_GLASS,
    FULL_GLASS,
}

object EffectsTierManager {
    fun determineTier(context: Context, userEnabled: Boolean): EffectsTier {
        if (!userEnabled) return EffectsTier.DISABLED
        if (!isRenderEffectSupported()) return EffectsTier.DISABLED

        val memMb = Runtime.getRuntime().maxMemory() / 1024 / 1024
        return when {
            memMb >= 512 -> EffectsTier.FULL_GLASS
            memMb >= 256 -> EffectsTier.LIGHT_GLASS
            else -> EffectsTier.BLUR_ONLY
        }
    }

    fun glassBlurRadius(tier: EffectsTier): Float = when (tier) {
        EffectsTier.FULL_GLASS -> 60f
        EffectsTier.LIGHT_GLASS -> 30f
        else -> 0f
    }

    fun glassNoiseCoefficient(tier: EffectsTier): Float = when (tier) {
        EffectsTier.FULL_GLASS -> 0.001f
        else -> 0f
    }
}
