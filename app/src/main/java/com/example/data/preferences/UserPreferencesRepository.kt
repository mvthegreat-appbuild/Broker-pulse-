package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "broker_pulse_prefs")

data class UserPreferences(
    val notificationsEnabled: Boolean = true,
    val pollingIntervalSeconds: Int = 15,
    val autoEvaluateTriggers: Boolean = true,
    val defaultQuantity: Int = 10,
    val themeMode: String = "DARK" // DARK, LIGHT, SYSTEM
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val POLLING_INTERVAL = intPreferencesKey("polling_interval_seconds")
        val AUTO_EVALUATE = booleanPreferencesKey("auto_evaluate_triggers")
        val DEFAULT_QUANTITY = intPreferencesKey("default_quantity")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            pollingIntervalSeconds = prefs[Keys.POLLING_INTERVAL] ?: 15,
            autoEvaluateTriggers = prefs[Keys.AUTO_EVALUATE] ?: true,
            defaultQuantity = prefs[Keys.DEFAULT_QUANTITY] ?: 10,
            themeMode = prefs[Keys.THEME_MODE] ?: "DARK"
        )
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setPollingIntervalSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.POLLING_INTERVAL] = seconds.coerceIn(10, 120) }
    }

    suspend fun setAutoEvaluateTriggers(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_EVALUATE] = enabled }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }
}
