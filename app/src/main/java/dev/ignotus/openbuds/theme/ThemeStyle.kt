package dev.ignotus.openbuds.theme

import androidx.compose.ui.graphics.Color

data class OpenBudsThemeConfig(
    val style: Int,   // 0 = Material, 1 = Miuix
    val colorMode: Int, // 0 = System, 1 = Light, 2 = Dark
    val seedColorIndex: Int = 0,
    val seedColor: Color? = null,
    val darkTheme: Boolean,
) {
    val isMiuix: Boolean get() = style == 1
}
