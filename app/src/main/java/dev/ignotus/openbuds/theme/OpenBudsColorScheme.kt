package dev.ignotus.openbuds.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val OpenBudsLightColors = lightColorScheme(
    primary = Color(0xFF275EA8),
    onPrimary = Color.White,
    secondary = Color(0xFF46617D),
    tertiary = Color(0xFF286B5D),
    background = Color(0xFFF6F7F9),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6EBF2),
    outline = Color(0xFFB8C1CC),
)

val OpenBudsDarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF00315F),
    secondary = Color(0xFFB7C9DF),
    tertiary = Color(0xFF8BD1BF),
    background = Color(0xFF101317),
    surface = Color(0xFF171B20),
    surfaceVariant = Color(0xFF303741),
    outline = Color(0xFF7E8A97),
)
