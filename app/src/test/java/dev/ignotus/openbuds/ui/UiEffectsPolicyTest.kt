package dev.ignotus.openbuds.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiEffectsPolicyTest {
    @Test
    fun effectsOff_disablesAllBackdropPathsForEveryMode() {
        ReareyeNavigationBarMode.entries.forEach { mode ->
            val capabilities = UiRenderCapabilities(
                mode = mode,
                tier = EffectsTier.DISABLED,
            )

            assertFalse(capabilities.effectsEnabled)
            assertFalse(capabilities.rootBackdropEnabled)
            assertFalse(capabilities.navigationBackdropEnabled)
            assertFalse(capabilities.liquidGlassEnabled)
            assertFalse(capabilities.glassCardsEnabled)
            assertFalse(capabilities.backgroundGradientEnabled)
        }
    }

    @Test
    fun effectsOn_enablesModeSpecificRenderPathsWithoutDeviceBlocking() {
        val expected = mapOf(
            ReareyeNavigationBarMode.Normal to ExpectedCapabilities(
                floating = false,
                semiTransparent = false,
                rootBackdrop = false,
                navigationBackdrop = false,
                liquidGlass = false,
                glassCards = true,
            ),
            ReareyeNavigationBarMode.SemiTransparent to ExpectedCapabilities(
                floating = false,
                semiTransparent = true,
                rootBackdrop = false,
                navigationBackdrop = false,
                liquidGlass = false,
                glassCards = true,
            ),
            ReareyeNavigationBarMode.Floating to ExpectedCapabilities(
                floating = true,
                semiTransparent = false,
                rootBackdrop = false,
                navigationBackdrop = false,
                liquidGlass = false,
                glassCards = true,
            ),
            ReareyeNavigationBarMode.FloatingGlass to ExpectedCapabilities(
                floating = true,
                semiTransparent = false,
                rootBackdrop = true,
                navigationBackdrop = true,
                liquidGlass = true,
                glassCards = true,
            ),
        )

        ReareyeNavigationBarMode.entries.forEach { mode ->
            val capabilities = UiRenderCapabilities(
                mode = mode,
                tier = EffectsTier.FULL_GLASS,
            )
            val expectedCapabilities = requireNotNull(expected[mode])

            assertTrue(capabilities.effectsEnabled)
            assertEquals(expectedCapabilities.floating, capabilities.floatingBottomBarEnabled)
            assertEquals(expectedCapabilities.semiTransparent, capabilities.semiTransparentBottomBar)
            assertEquals(expectedCapabilities.rootBackdrop, capabilities.rootBackdropEnabled)
            assertEquals(expectedCapabilities.navigationBackdrop, capabilities.navigationBackdropEnabled)
            assertEquals(expectedCapabilities.liquidGlass, capabilities.liquidGlassEnabled)
            assertEquals(expectedCapabilities.glassCards, capabilities.glassCardsEnabled)
            assertTrue(capabilities.backgroundGradientEnabled)
        }
    }

    @Test
    fun semiTransparentModeOnlyEnablesBottomBarSurface() {
        val capabilities = UiRenderCapabilities(
            mode = ReareyeNavigationBarMode.SemiTransparent,
            tier = EffectsTier.FULL_GLASS,
        )

        assertTrue(capabilities.effectsEnabled)
        assertTrue(capabilities.semiTransparentBottomBar)
        assertFalse(capabilities.floatingBottomBarEnabled)
        assertFalse(capabilities.rootBackdropEnabled)
        assertFalse(capabilities.navigationBackdropEnabled)
        assertFalse(capabilities.liquidGlassEnabled)
        assertTrue(capabilities.glassCardsEnabled)
    }

    private data class ExpectedCapabilities(
        val floating: Boolean,
        val semiTransparent: Boolean,
        val rootBackdrop: Boolean,
        val navigationBackdrop: Boolean,
        val liquidGlass: Boolean,
        val glassCards: Boolean,
    )
}
