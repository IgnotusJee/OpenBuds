package dev.ignotus.sonyrebuild.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF275EA8),
    onPrimary = Color.White,
    secondary = Color(0xFF46617D),
    tertiary = Color(0xFF286B5D),
    background = Color(0xFFF6F7F9),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6EBF2),
    outline = Color(0xFFB8C1CC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF00315F),
    secondary = Color(0xFFB7C9DF),
    tertiary = Color(0xFF8BD1BF),
    background = Color(0xFF101317),
    surface = Color(0xFF171B20),
    surfaceVariant = Color(0xFF303741),
    outline = Color(0xFF7E8A97),
)

@Composable
fun SonyRebuildTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    configureSystemBars: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
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
