package dev.ignotus.openbuds.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController as MiuixThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

@Composable
fun OpenBudsTheme(
    config: OpenBudsThemeConfig,
    content: @Composable () -> Unit,
) {
    val colorScheme: ColorScheme = remember(config) { resolveOpenBudsColorScheme(config) }

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
            MaterialTheme(colorScheme = colorScheme, typography = MiuixTypography) {
                content()
            }
        }
    } else {
        MaterialTheme(colorScheme = colorScheme, typography = OpenBudsTypography) {
            content()
        }
    }
}

fun resolveOpenBudsColorScheme(config: OpenBudsThemeConfig): ColorScheme {
    if (config.isMiuix) {
        return if (config.darkTheme) MiuixDarkColors else MiuixLightColors
    }
    return seedColorScheme(config.seedColorIndex, config.darkTheme)
}
