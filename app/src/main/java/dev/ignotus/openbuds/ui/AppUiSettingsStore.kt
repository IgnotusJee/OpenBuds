package dev.ignotus.openbuds.ui

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import dev.ignotus.openbuds.R

private val Context.openbudsUiSettingsDataStore by preferencesDataStore("openbuds_ui_settings_v2")

enum class AppColorMode(val titleResId: Int) {
    Light(R.string.color_mode_light),
    Dark(R.string.color_mode_dark),
    System(R.string.color_mode_system),
}

data class AppUiSettings(
    val navigationBarMode: String = ReareyeNavigationBarMode.Floating.name,
    val themeStyle: String = ThemeStyle.Material.name,
    val colorMode: String = AppColorMode.System.name,
    val effectsEnabled: Boolean = false,
    val serviceBackgroundRun: Boolean = false,
    val notificationPersistent: Boolean = true,
    val notificationLockscreen: Boolean = true,
    val connectionPopup: Boolean = false,
    val hyperOsNotification: Boolean = false,
    val controlCenterIntercept: Boolean = false,
)

class AppUiSettingsStore(private val context: Context) {
    val settings: Flow<AppUiSettings> = context.openbudsUiSettingsDataStore.data.map { prefs ->
        AppUiSettings(
            navigationBarMode = prefs[NavigationBarModeKey] ?: ReareyeNavigationBarMode.Floating.name,
            themeStyle = prefs[ThemeStyleKey] ?: ThemeStyle.Material.name,
            colorMode = prefs[ColorModeKey] ?: AppColorMode.System.name,
            effectsEnabled = prefs[EffectsEnabledKey] ?: false,
            serviceBackgroundRun = prefs[ServiceBackgroundRunKey] ?: false,
            notificationPersistent = prefs[NotificationPersistentKey] ?: true,
            notificationLockscreen = prefs[NotificationLockscreenKey] ?: true,
            connectionPopup = prefs[ConnectionPopupKey] ?: false,
            hyperOsNotification = prefs[HyperOsNotificationKey] ?: false,
            controlCenterIntercept = prefs[ControlCenterInterceptKey] ?: false,
        )
    }

    suspend fun setNavigationBarMode(mode: ReareyeNavigationBarMode) {
        context.openbudsUiSettingsDataStore.edit { prefs ->
            prefs[NavigationBarModeKey] = mode.name
        }
    }

    suspend fun setThemeStyle(style: ThemeStyle) {
        context.openbudsUiSettingsDataStore.edit { prefs ->
            prefs[ThemeStyleKey] = style.name
        }
    }

    suspend fun setColorMode(mode: AppColorMode) {
        context.openbudsUiSettingsDataStore.edit { prefs ->
            prefs[ColorModeKey] = mode.name
        }
    }

    suspend fun setEffectsEnabled(enabled: Boolean) {
        context.openbudsUiSettingsDataStore.edit { prefs ->
            prefs[EffectsEnabledKey] = enabled
        }
    }

    suspend fun setServiceBackgroundRun(enabled: Boolean) {
        context.openbudsUiSettingsDataStore.edit { prefs ->
            prefs[ServiceBackgroundRunKey] = enabled
        }
    }

    suspend fun setNotificationPersistent(enabled: Boolean) {
        context.openbudsUiSettingsDataStore.edit { prefs ->
            prefs[NotificationPersistentKey] = enabled
        }
    }

    suspend fun setNotificationLockscreen(enabled: Boolean) {
        context.openbudsUiSettingsDataStore.edit { prefs ->
            prefs[NotificationLockscreenKey] = enabled
        }
    }

    suspend fun setConnectionPopup(enabled: Boolean) {
        context.openbudsUiSettingsDataStore.edit { prefs ->
            prefs[ConnectionPopupKey] = enabled
        }
    }

    suspend fun setHyperOsNotification(enabled: Boolean) {
        context.openbudsUiSettingsDataStore.edit { prefs ->
            prefs[HyperOsNotificationKey] = enabled
        }
    }

    suspend fun setControlCenterIntercept(enabled: Boolean) {
        context.openbudsUiSettingsDataStore.edit { prefs ->
            prefs[ControlCenterInterceptKey] = enabled
        }
    }

    private companion object {
        val NavigationBarModeKey = stringPreferencesKey("navigation_bar_mode")
        val ThemeStyleKey = stringPreferencesKey("theme_style")
        val ColorModeKey = stringPreferencesKey("color_mode")
        val EffectsEnabledKey = booleanPreferencesKey("effects_enabled")
        val ServiceBackgroundRunKey = booleanPreferencesKey("service_background_run")
        val NotificationPersistentKey = booleanPreferencesKey("notification_persistent")
        val NotificationLockscreenKey = booleanPreferencesKey("notification_lockscreen")
        val ConnectionPopupKey = booleanPreferencesKey("connection_popup")
        val HyperOsNotificationKey = booleanPreferencesKey("hyper_os_notification")
        val ControlCenterInterceptKey = booleanPreferencesKey("control_center_intercept")
    }
}

fun resolveDarkTheme(colorMode: AppColorMode, systemDarkTheme: Boolean): Boolean =
    when (colorMode) {
        AppColorMode.Light -> false
        AppColorMode.Dark -> true
        AppColorMode.System -> systemDarkTheme
    }
