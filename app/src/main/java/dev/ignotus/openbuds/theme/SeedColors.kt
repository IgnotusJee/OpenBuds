package dev.ignotus.openbuds.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

data class SeedColorEntry(
    val name: String,
    val color: Color,
    val lightScheme: ColorScheme,
    val darkScheme: ColorScheme,
)

val OpenBudsSeedColors: List<SeedColorEntry> = listOf(
    SeedColorEntry("Default", Color(0xFF6750A4), OpenBudsLightColors, OpenBudsDarkColors),
    SeedColorEntry("Blue", Color(0xFF275EA8),
        lightColorScheme(primary = Color(0xFF275EA8)),
        darkColorScheme(primary = Color(0xFFA9C7FF))),
    SeedColorEntry("Miuix Blue", Color(0xFF1F6FEB),
        lightColorScheme(primary = Color(0xFF1F6FEB)),
        darkColorScheme(primary = Color(0xFF8AB4FF))),
)
