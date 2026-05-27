package dev.ignotus.openbuds.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

fun miuixLikeColorScheme(base: ColorScheme, darkTheme: Boolean): ColorScheme =
    if (darkTheme) {
        base.copy(
            primary = Color(0xFF8AB4FF),
            onPrimary = Color(0xFF062D5F),
            secondary = Color(0xFFC2CAD6),
            tertiary = Color(0xFF77D0BE),
            background = Color(0xFF101114),
            onBackground = Color(0xFFE7E8EC),
            surface = Color(0xFF18191D),
            onSurface = Color(0xFFE7E8EC),
            surfaceVariant = Color(0xFF25272D),
            onSurfaceVariant = Color(0xFFC5C8D0),
            outline = Color(0xFF676B75),
        )
    } else {
        base.copy(
            primary = Color(0xFF1F6FEB),
            onPrimary = Color.White,
            secondary = Color(0xFF5E6B7A),
            tertiary = Color(0xFF0F8F7A),
            background = Color(0xFFF7F8FA),
            onBackground = Color(0xFF181A20),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF181A20),
            surfaceVariant = Color(0xFFECEFF4),
            onSurfaceVariant = Color(0xFF59616E),
            outline = Color(0xFFC7CCD4),
        )
    }
