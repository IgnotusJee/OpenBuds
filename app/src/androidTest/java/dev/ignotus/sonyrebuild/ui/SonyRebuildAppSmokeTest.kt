package dev.ignotus.sonyrebuild.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import dev.ignotus.sonyrebuild.data.SonyHeadphoneUiState
import dev.ignotus.sonyrebuild.theme.SonyRebuildTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class SonyRebuildAppSmokeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun homePage_rendersEmptyConnectionState() {
        composeTestRule.setContent {
            SonyRebuildTheme {
                SmokeSonyRebuildApp()
            }
        }

        composeTestRule.onNodeWithText("SonyRebuild").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connection").assertIsDisplayed()
        composeTestRule.onNodeWithText("No Sony Tandem V2 devices found yet.").assertIsDisplayed()
    }

    @Test
    fun homePage_rendersWithLiquidGlassMiuixEffectsEnabled() {
        runBlocking {
            val settingsStore = AppUiSettingsStore(ApplicationProvider.getApplicationContext())
            settingsStore.setNavigationBarMode(ReareyeNavigationBarMode.FloatingGlass)
            settingsStore.setThemeStyle(ThemeStyle.Miuix)
            settingsStore.setEffectsEnabled(true)
        }

        composeTestRule.setContent {
            SonyRebuildTheme {
                SmokeSonyRebuildApp()
            }
        }

        composeTestRule.onNodeWithText("SonyRebuild").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connection").assertIsDisplayed()
        composeTestRule.onNodeWithText("No Sony Tandem V2 devices found yet.").assertIsDisplayed()
    }

    @Test
    fun colorModeSwitch_keepsAppearanceRouteVisible() {
        runBlocking {
            val settingsStore = AppUiSettingsStore(ApplicationProvider.getApplicationContext())
            settingsStore.setThemeStyle(ThemeStyle.Material)
            settingsStore.setColorMode(AppColorMode.System)
        }

        composeTestRule.setContent {
            SonyRebuildTheme {
                SmokeSonyRebuildApp()
            }
        }

        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.onNodeWithText("Appearance").performClick()
        composeTestRule.onNodeWithText("颜色模式").assertIsDisplayed()
        composeTestRule.onNodeWithText("Theme style").assertIsDisplayed()
        composeTestRule.onNodeWithText("深色").performClick()

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("颜色模式").assertIsDisplayed()
        composeTestRule.onNodeWithText("Theme style").assertIsDisplayed()
    }
}

@Composable
private fun SmokeSonyRebuildApp() {
    SonyRebuildApp(
        state = SonyHeadphoneUiState(),
        onStartScan = {},
        onStopScan = {},
        onConnect = {},
        onDisconnect = {},
        onRefresh = {},
        onSetNoiseControlMode = {},
        onSetAmbientLevel = {},
        onSetAmbientVoiceMode = {},
        onSetEqPreset = {},
        onSetClearBass = {},
        onSetCustomEqBand = { _, _ -> },
        onPlaybackPrevious = {},
        onPlaybackPlayPause = {},
        onPlaybackNext = {},
        onDebugLoggingChanged = {},
        onAutoReconnectChanged = {},
        onStrictScanFilterChanged = {},
    )
}
