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
private const val DEFAULT_MANAGED_CF_API_URL = "https://676967.xyz/provision"

// Single provider - Cloudflare managed tunnels via 676967.xyz
// Each device gets a unique hostname like abc123.676967.xyz

object PreferencesKeys {
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
    val LAST_TUNNEL_URL = stringPreferencesKey("last_tunnel_url")
    val ADB_PORT = stringPreferencesKey("adb_port")

    // Managed Cloudflare (provisioned by Worker at api.676967.xyz)
    val MANAGED_CF_DEVICE_ID = stringPreferencesKey("managed_cf_device_id")
    val MANAGED_CF_HOSTNAME = stringPreferencesKey("managed_cf_hostname")
    val MANAGED_CF_RUN_TOKEN = stringPreferencesKey("managed_cf_run_token")
}

class SettingsRepository(private val context: Context) {
    
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

    val managedCfHostname: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.MANAGED_CF_HOSTNAME] ?: ""
        }

    val managedCfRunToken: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.MANAGED_CF_RUN_TOKEN] ?: ""
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

    fun getApiUrl(): String = DEFAULT_MANAGED_CF_API_URL
    fun getBaseDomain(): String = DEFAULT_MANAGED_CF_BASE_DOMAIN
}
