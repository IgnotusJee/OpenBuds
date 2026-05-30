package dev.ignotus.openbuds.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import dev.ignotus.openbuds.data.HeadphoneUiState
import dev.ignotus.openbuds.theme.OpenBudsTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class OpenBudsAppSmokeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun homePage_rendersEmptyConnectionState() {
        composeTestRule.setContent {
            OpenBudsTheme {
                SmokeOpenBudsApp()
            }
        }

        composeTestRule.onNodeWithText("OpenBuds").assertIsDisplayed()
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
            OpenBudsTheme {
                SmokeOpenBudsApp()
            }
        }

        composeTestRule.onNodeWithText("OpenBuds").assertIsDisplayed()
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
            OpenBudsTheme {
                SmokeOpenBudsApp()
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
private fun SmokeOpenBudsApp() {
    OpenBudsApp(
        state = HeadphoneUiState(),
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
