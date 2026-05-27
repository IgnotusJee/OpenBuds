package dev.ignotus.openbuds.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = OpenBudsLightColors
private val DarkColors = OpenBudsDarkColors

fun openbudsColorScheme(darkTheme: Boolean): ColorScheme = if (darkTheme) DarkColors else LightColors

@Composable
fun OpenBudsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    configureSystemBars: Boolean = true,
    content: @Composable () -> Unit,
) {
    val config = remember(darkTheme) {
        OpenBudsThemeConfig(
            style = 0,
            colorMode = if (darkTheme) 2 else 1,
            darkTheme = darkTheme,
        )
    }
    if (configureSystemBars) {
        val colorScheme = openbudsColorScheme(darkTheme)
        ConfigureSystemBars(colorScheme, darkTheme)
    }
    dev.ignotus.openbuds.theme.OpenBudsTheme(config = config, content = content)
}

@Composable
private fun ConfigureSystemBars(colorScheme: ColorScheme, darkTheme: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
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
