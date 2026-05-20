package dev.ignotus.sonyrebuild.ui

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sonyRebuildUiSettingsDataStore by preferencesDataStore("sony_rebuild_ui_settings_v2")

data class AppUiSettings(
    val navigationBarMode: String = ReareyeNavigationBarMode.Floating.name,
    val themeStyle: String = ThemeStyle.Material.name,
    val effectsEnabled: Boolean = false,
)

class AppUiSettingsStore(private val context: Context) {
    val settings: Flow<AppUiSettings> = context.sonyRebuildUiSettingsDataStore.data.map { prefs ->
        AppUiSettings(
            navigationBarMode = prefs[NavigationBarModeKey] ?: ReareyeNavigationBarMode.Floating.name,
            themeStyle = prefs[ThemeStyleKey] ?: ThemeStyle.Material.name,
            effectsEnabled = prefs[EffectsEnabledKey] ?: false,
        )
    }

    suspend fun setNavigationBarMode(mode: ReareyeNavigationBarMode) {
        context.sonyRebuildUiSettingsDataStore.edit { prefs ->
            prefs[NavigationBarModeKey] = mode.name
        }
    }

    suspend fun setThemeStyle(style: ThemeStyle) {
        context.sonyRebuildUiSettingsDataStore.edit { prefs ->
            prefs[ThemeStyleKey] = style.name
        }
    }

    suspend fun setEffectsEnabled(enabled: Boolean) {
        context.sonyRebuildUiSettingsDataStore.edit { prefs ->
            prefs[EffectsEnabledKey] = enabled
        }
    }

    private companion object {
        val NavigationBarModeKey = stringPreferencesKey("navigation_bar_mode")
        val ThemeStyleKey = stringPreferencesKey("theme_style")
        val EffectsEnabledKey = booleanPreferencesKey("effects_enabled")
    }
}
