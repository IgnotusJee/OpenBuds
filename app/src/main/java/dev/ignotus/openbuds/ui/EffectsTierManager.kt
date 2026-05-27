package dev.ignotus.openbuds.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Build
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

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = am?.isLowRamDevice ?: false
        if (isLowRam) return EffectsTier.BLUR_ONLY

        val memMb = am?.memoryClass?.toLong() ?: 0L
        val sdk = Build.VERSION.SDK_INT

        return when {
            memMb >= 256 && sdk >= Build.VERSION_CODES.S -> EffectsTier.FULL_GLASS
            memMb >= 128 && sdk >= Build.VERSION_CODES.R -> EffectsTier.LIGHT_GLASS
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
