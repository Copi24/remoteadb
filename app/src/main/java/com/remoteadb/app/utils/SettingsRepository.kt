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

enum class TunnelProvider(val displayName: String, val description: String) {
    MANUAL("Manual", "Use Termux/VPN/SSH (recommended)"),
    CLOUDFLARE("Cloudflare", "Experimental (TCP may not work)"),
    NGROK("Ngrok", "TCP often requires paid plan")
}

object PreferencesKeys {
    val NGROK_AUTH_TOKEN = stringPreferencesKey("ngrok_auth_token")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
    val LAST_TUNNEL_URL = stringPreferencesKey("last_tunnel_url")
    val ADB_PORT = stringPreferencesKey("adb_port")
    val TUNNEL_PROVIDER = stringPreferencesKey("tunnel_provider")
    val CLOUDFLARED_DOWNLOADED = booleanPreferencesKey("cloudflared_downloaded")
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
    
    val tunnelProvider: Flow<TunnelProvider> = context.dataStore.data
        .map { preferences ->
            val providerName = preferences[PreferencesKeys.TUNNEL_PROVIDER] ?: TunnelProvider.MANUAL.name
            try {
                TunnelProvider.valueOf(providerName)
            } catch (e: Exception) {
                TunnelProvider.CLOUDFLARE
            }
        }
    
    val cloudflaredDownloaded: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.CLOUDFLARED_DOWNLOADED] ?: false
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
    
    suspend fun setTunnelProvider(provider: TunnelProvider) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TUNNEL_PROVIDER] = provider.name
        }
    }
    
    suspend fun setCloudflaredDownloaded(downloaded: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CLOUDFLARED_DOWNLOADED] = downloaded
        }
    }
}
