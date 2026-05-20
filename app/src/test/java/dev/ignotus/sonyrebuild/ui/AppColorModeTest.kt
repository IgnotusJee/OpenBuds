package dev.ignotus.sonyrebuild.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppColorModeTest {
    @Test
    fun lightModeAlwaysResolvesLight() {
        assertFalse(resolveDarkTheme(AppColorMode.Light, systemDarkTheme = false))
        assertFalse(resolveDarkTheme(AppColorMode.Light, systemDarkTheme = true))
    }

    @Test
    fun darkModeAlwaysResolvesDark() {
        assertTrue(resolveDarkTheme(AppColorMode.Dark, systemDarkTheme = false))
        assertTrue(resolveDarkTheme(AppColorMode.Dark, systemDarkTheme = true))
    }

    @Test
    fun systemModeFollowsSystemTheme() {
        assertFalse(resolveDarkTheme(AppColorMode.System, systemDarkTheme = false))
        assertTrue(resolveDarkTheme(AppColorMode.System, systemDarkTheme = true))
    }
}
