package com.remoteadb.app.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "remote_adb_settings")

object PreferencesKeys {
    val NGROK_AUTH_TOKEN = stringPreferencesKey("ngrok_auth_token")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
    val LAST_TUNNEL_URL = stringPreferencesKey("last_tunnel_url")
    val ADB_PORT = stringPreferencesKey("adb_port")
}

class SettingsRepository(private val context: Context) {
    
    val ngrokAuthToken: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.NGROK_AUTH_TOKEN] ?: ""
        }
    
    val onboardingCompleted: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.ONBOARDING_COMPLETED] ?: false
        }
    
    val autoStartOnBoot: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.AUTO_START_ON_BOOT] ?: false
        }
    
    val lastTunnelUrl: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LAST_TUNNEL_URL] ?: ""
        }
    
    val adbPort: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.ADB_PORT] ?: "5555"
        }
    
    suspend fun setNgrokAuthToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NGROK_AUTH_TOKEN] = token
        }
    }
    
    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ONBOARDING_COMPLETED] = completed
        }
    }
    
    suspend fun setAutoStartOnBoot(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_START_ON_BOOT] = enabled
        }
    }
    
    suspend fun setLastTunnelUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_TUNNEL_URL] = url
        }
    }
    
    suspend fun setAdbPort(port: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ADB_PORT] = port
        }
    }
}
