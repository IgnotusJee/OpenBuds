package dev.ignotus.openbuds.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.openbudsUiSettingsDataStore by preferencesDataStore("openbuds_ui_settings_v2")

data class AppSettings(
    val serviceBackgroundRun: Boolean = false,
    val notificationPersistent: Boolean = true,
    val notificationLockscreen: Boolean = true,
    val connectionPopup: Boolean = false,
    val milinkAdapterEnabled: Boolean = true,
)

class AppSettingsStore(private val context: Context) {
    val settings: Flow<AppSettings> = context.openbudsUiSettingsDataStore.data.map { prefs ->
        AppSettings(
            serviceBackgroundRun = prefs[ServiceBackgroundRunKey] ?: false,
            notificationPersistent = prefs[NotificationPersistentKey] ?: true,
            notificationLockscreen = prefs[NotificationLockscreenKey] ?: true,
            connectionPopup = prefs[ConnectionPopupKey] ?: false,
            milinkAdapterEnabled = prefs[MilinkAdapterEnabledKey] ?: false,
        )
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

    suspend fun setMilinkAdapterEnabled(enabled: Boolean) {
        context.openbudsUiSettingsDataStore.edit { prefs ->
            prefs[MilinkAdapterEnabledKey] = enabled
        }
    }

    private companion object {
        val ServiceBackgroundRunKey = booleanPreferencesKey("service_background_run")
        val NotificationPersistentKey = booleanPreferencesKey("notification_persistent")
        val NotificationLockscreenKey = booleanPreferencesKey("notification_lockscreen")
        val ConnectionPopupKey = booleanPreferencesKey("connection_popup")
        val MilinkAdapterEnabledKey = booleanPreferencesKey("milink_adapter_enabled")
    }
}
