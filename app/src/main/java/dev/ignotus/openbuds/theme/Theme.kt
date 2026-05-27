package dev.ignotus.openbuds.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
    val colorScheme = openbudsColorScheme(darkTheme)
    if (configureSystemBars) {
        ConfigureSystemBars(colorScheme, darkTheme)
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
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
