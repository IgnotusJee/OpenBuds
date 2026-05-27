package dev.ignotus.openbuds.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

data class SeedColorEntry(
    val nameResId: Int,
    val color: Color,
    val lightScheme: ColorScheme,
    val darkScheme: ColorScheme,
)

val OpenBudsSeedColors: List<SeedColorEntry> = listOf(
    SeedColorEntry(dev.ignotus.openbuds.R.string.seed_color_system, Color(0xFF6750A4), OpenBudsLightColors, OpenBudsDarkColors),
    SeedColorEntry(dev.ignotus.openbuds.R.string.seed_color_blue, Color(0xFF275EA8), lightColorScheme(primary = Color(0xFF275EA8)), darkColorScheme(primary = Color(0xFFA9C7FF))),
    SeedColorEntry(dev.ignotus.openbuds.R.string.seed_color_miuix_blue, Color(0xFF1F6FEB), lightColorScheme(primary = Color(0xFF1F6FEB)), darkColorScheme(primary = Color(0xFF8AB4FF))),
    SeedColorEntry(dev.ignotus.openbuds.R.string.seed_color_red, Color(0xFFB3261E), lightColorScheme(primary = Color(0xFFB3261E)), darkColorScheme(primary = Color(0xFFFFB4AB))),
    SeedColorEntry(dev.ignotus.openbuds.R.string.seed_color_pink, Color(0xFFBA1A60), lightColorScheme(primary = Color(0xFFBA1A60)), darkColorScheme(primary = Color(0xFFFFB1C8))),
    SeedColorEntry(dev.ignotus.openbuds.R.string.seed_color_purple, Color(0xFF6750A4), lightColorScheme(primary = Color(0xFF6750A4)), darkColorScheme(primary = Color(0xFFD0BCFF))),
    SeedColorEntry(dev.ignotus.openbuds.R.string.seed_color_indigo, Color(0xFF3F51B5), lightColorScheme(primary = Color(0xFF3F51B5)), darkColorScheme(primary = Color(0xFFC1C9FF))),
    SeedColorEntry(dev.ignotus.openbuds.R.string.seed_color_cyan, Color(0xFF006A6A), lightColorScheme(primary = Color(0xFF006A6A)), darkColorScheme(primary = Color(0xFF82D3D3))),
    SeedColorEntry(dev.ignotus.openbuds.R.string.seed_color_teal, Color(0xFF006B5B), lightColorScheme(primary = Color(0xFF006B5B)), darkColorScheme(primary = Color(0xFF83D5C2))),
    SeedColorEntry(dev.ignotus.openbuds.R.string.seed_color_green, Color(0xFF386A20), lightColorScheme(primary = Color(0xFF386A20)), darkColorScheme(primary = Color(0xFF9BD67D))),
    SeedColorEntry(dev.ignotus.openbuds.R.string.seed_color_lime, Color(0xFF5B6400), lightColorScheme(primary = Color(0xFF5B6400)), darkColorScheme(primary = Color(0xFFD0DB6B))),
    SeedColorEntry(dev.ignotus.openbuds.R.string.seed_color_amber, Color(0xFF7D5700), lightColorScheme(primary = Color(0xFF7D5700)), darkColorScheme(primary = Color(0xFFFFC95C))),
    SeedColorEntry(dev.ignotus.openbuds.R.string.seed_color_orange, Color(0xFF9A4600), lightColorScheme(primary = Color(0xFF9A4600)), darkColorScheme(primary = Color(0xFFFFB787))),
    SeedColorEntry(dev.ignotus.openbuds.R.string.seed_color_brown, Color(0xFF735244), lightColorScheme(primary = Color(0xFF735244)), darkColorScheme(primary = Color(0xFFE3BFAE))),
    SeedColorEntry(dev.ignotus.openbuds.R.string.seed_color_blue_grey, Color(0xFF546E7A), lightColorScheme(primary = Color(0xFF546E7A)), darkColorScheme(primary = Color(0xFFB8CAD3))),
    SeedColorEntry(dev.ignotus.openbuds.R.string.seed_color_sakura, Color(0xFFFF8FAB), lightColorScheme(primary = Color(0xFF98405A)), darkColorScheme(primary = Color(0xFFFFB1C8))),
)

fun normalizedSeedColorIndex(index: Int): Int =
    index.coerceIn(OpenBudsSeedColors.indices)

fun seedColorOrNull(index: Int): Color? =
    OpenBudsSeedColors.getOrNull(normalizedSeedColorIndex(index))
        ?.color
        ?.takeIf { normalizedSeedColorIndex(index) != 0 }

fun seedColorScheme(index: Int, darkTheme: Boolean): ColorScheme {
    val entry = OpenBudsSeedColors[normalizedSeedColorIndex(index)]
    return if (darkTheme) entry.darkScheme else entry.lightScheme
}
