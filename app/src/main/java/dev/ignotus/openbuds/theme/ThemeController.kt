package dev.ignotus.openbuds.theme

import androidx.compose.material3.MaterialTheme
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
    val colorScheme: androidx.compose.material3.ColorScheme = remember(config) {
        if (config.darkTheme) OpenBudsDarkColors else OpenBudsLightColors
    }

    if (config.isMiuix) {
        val schemeMode = when (config.colorMode) {
            0 -> ColorSchemeMode.System
            1 -> ColorSchemeMode.Light
            2 -> ColorSchemeMode.Dark
            else -> ColorSchemeMode.System
        }
        MiuixTheme(
            controller = MiuixThemeController(schemeMode, keyColor = config.seedColor),
        ) {
            MaterialTheme(colorScheme = colorScheme, typography = OpenBudsTypography) {
                content()
            }
        }
    } else {
        MaterialTheme(colorScheme = colorScheme, typography = OpenBudsTypography) {
            content()
        }
    }
}
