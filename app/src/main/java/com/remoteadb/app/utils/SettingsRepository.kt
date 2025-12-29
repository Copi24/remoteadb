package com.remoteadb.app.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "remote_adb_settings")

private const val DEFAULT_MANAGED_CF_BASE_DOMAIN = "676967.xyz"
private const val DEFAULT_MANAGED_CF_API_URL = "https://api.676967.xyz/provision"

enum class TunnelProvider(val displayName: String, val description: String) {
    MANUAL("Manual", "Use Termux/VPN/SSH (recommended)"),
    CLOUDFLARE_MANAGED("Cloudflare (Managed)", "Your domain + per-device hostname"),
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

    // Managed Cloudflare (provisioned by your Worker/backend)
    val MANAGED_CF_BASE_DOMAIN = stringPreferencesKey("managed_cf_base_domain")
    val MANAGED_CF_API_URL = stringPreferencesKey("managed_cf_api_url")
    val MANAGED_CF_DEVICE_ID = stringPreferencesKey("managed_cf_device_id")
    val MANAGED_CF_HOSTNAME = stringPreferencesKey("managed_cf_hostname")
    val MANAGED_CF_RUN_TOKEN = stringPreferencesKey("managed_cf_run_token")

    // Legacy
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
            val providerName = preferences[PreferencesKeys.TUNNEL_PROVIDER] ?: TunnelProvider.CLOUDFLARE_MANAGED.name
            try {
                TunnelProvider.valueOf(providerName)
            } catch (e: Exception) {
                TunnelProvider.MANUAL
            }
        }
    
    val managedCfBaseDomain: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.MANAGED_CF_BASE_DOMAIN]
                ?.takeIf { it.isNotBlank() }
                ?: DEFAULT_MANAGED_CF_BASE_DOMAIN
        }

    val managedCfApiUrl: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.MANAGED_CF_API_URL]
                ?.takeIf { it.isNotBlank() }
                ?: DEFAULT_MANAGED_CF_API_URL
        }

    val managedCfHostname: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.MANAGED_CF_HOSTNAME] ?: ""
        }

    val managedCfRunToken: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.MANAGED_CF_RUN_TOKEN] ?: ""
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

    suspend fun setManagedCfBaseDomain(domain: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MANAGED_CF_BASE_DOMAIN] = domain
        }
    }

    suspend fun setManagedCfApiUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MANAGED_CF_API_URL] = url
        }
    }

    suspend fun setManagedCfProvisioning(hostname: String, runToken: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MANAGED_CF_HOSTNAME] = hostname
            preferences[PreferencesKeys.MANAGED_CF_RUN_TOKEN] = runToken
        }
    }

    suspend fun getOrCreateManagedDeviceId(): String {
        var id = context.dataStore.data.map { it[PreferencesKeys.MANAGED_CF_DEVICE_ID] ?: "" }.first()
        if (id.isBlank()) {
            id = UUID.randomUUID().toString().replace("-", "")
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.MANAGED_CF_DEVICE_ID] = id
            }
        }
        return id
    }

    suspend fun setCloudflaredDownloaded(downloaded: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CLOUDFLARED_DOWNLOADED] = downloaded
        }
    }
}
