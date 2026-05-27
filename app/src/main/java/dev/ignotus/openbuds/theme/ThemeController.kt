package dev.ignotus.openbuds.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.Colors as MiuixColors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController as MiuixThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

@Composable
fun OpenBudsTheme(
    config: OpenBudsThemeConfig,
    content: @Composable () -> Unit,
) {
    if (config.isMiuix) {
        val schemeMode = when (config.colorMode) {
            0 -> ColorSchemeMode.MonetSystem
            1 -> ColorSchemeMode.MonetLight
            2 -> ColorSchemeMode.MonetDark
            else -> ColorSchemeMode.MonetSystem
        }
        MiuixTheme(
            controller = MiuixThemeController(
                schemeMode,
                keyColor = config.seedColor ?: seedColorOrNull(config.seedColorIndex),
            ),
        ) {
            val miuixColors = MiuixTheme.colorScheme
            val materialCs = remember(miuixColors) { miuixToMaterialColorScheme(miuixColors, config.darkTheme) }
            MaterialTheme(colorScheme = materialCs, typography = MiuixTypography) {
                content()
            }
        }
    } else {
        val colorScheme: ColorScheme = remember(config) { resolveOpenBudsColorScheme(config) }
        MaterialTheme(colorScheme = colorScheme, typography = OpenBudsTypography) {
            content()
        }
    }
}

fun resolveOpenBudsColorScheme(config: OpenBudsThemeConfig): ColorScheme {
    return seedColorScheme(config.seedColorIndex, config.darkTheme)
}

private fun miuixToMaterialColorScheme(miuix: MiuixColors, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = miuix.primary,
        onPrimary = miuix.onPrimary,
        primaryContainer = miuix.primaryContainer,
        onPrimaryContainer = miuix.onPrimaryContainer,
        secondary = miuix.secondary,
        onSecondary = miuix.onSecondary,
        secondaryContainer = miuix.secondaryContainer,
        onSecondaryContainer = miuix.onSecondaryContainer,
        tertiary = miuix.primary,
        onTertiary = miuix.onPrimary,
        tertiaryContainer = miuix.tertiaryContainer,
        onTertiaryContainer = miuix.onTertiaryContainer,
        background = miuix.background,
        onBackground = miuix.onBackground,
        surface = miuix.surface,
        onSurface = miuix.onSurface,
        surfaceVariant = miuix.surfaceVariant,
        onSurfaceVariant = miuix.onSurfaceSecondary,
        error = miuix.onSurface.copy(alpha = 0.8f),
        onError = miuix.surface,
        errorContainer = miuix.onSurface.copy(alpha = 0.15f),
        onErrorContainer = miuix.onSurface,
        outline = miuix.outline,
        outlineVariant = miuix.dividerLine,
    )
}
