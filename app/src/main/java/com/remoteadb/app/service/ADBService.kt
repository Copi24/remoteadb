package com.remoteadb.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.remoteadb.app.MainActivity
import com.remoteadb.app.R
import com.remoteadb.app.utils.ADBManager
import com.remoteadb.app.utils.ManagedCloudflareProvisioner
import com.remoteadb.app.utils.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ADBService : Service() {
    
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var settingsRepository: SettingsRepository
    private var cloudflaredProcess: Process? = null
    
    private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.Stopped)
    val serviceState: StateFlow<ServiceState> = _serviceState
    
    private val _tunnelUrl = MutableStateFlow<String?>(null)
    val tunnelUrl: StateFlow<String?> = _tunnelUrl
    
    inner class LocalBinder : Binder() {
        fun getService(): ADBService = this@ADBService
    }
    
    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startAdbTunnel()
            ACTION_STOP -> stopAdbTunnel()
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        cloudflaredProcess?.destroy()
    }
    
    private fun startAdbTunnel() {
        serviceScope.launch {
            _serviceState.value = ServiceState.Starting
            
            startForeground(NOTIFICATION_ID, createNotification("Starting ADB tunnel..."))
            
            val port = settingsRepository.adbPort.first().toIntOrNull() ?: 5555
            
            // Enable TCP ADB
            updateNotification("Enabling ADB over TCP...")
            val adbResult = ADBManager.enableTcpAdb(port)
            if (!adbResult.success) {
                _serviceState.value = ServiceState.Error(adbResult.error ?: "Failed to enable ADB over TCP")
                stopSelf()
                return@launch
            }
            
            // Small delay to ensure ADB is ready
            kotlinx.coroutines.delay(1000)
            
            // Provision hostname via Cloudflare Worker
            updateNotification("Provisioning tunnel...")
            val apiUrl = settingsRepository.getApiUrl()
            val domain = settingsRepository.getBaseDomain()
            val deviceId = settingsRepository.getOrCreateManagedDeviceId()
            
            try {
                val provisioned = ManagedCloudflareProvisioner.provision(apiUrl, domain, deviceId)
                settingsRepository.setManagedCfProvisioning(provisioned.hostname, provisioned.runToken)
                
                // Start cloudflared with the token
                updateNotification("Starting Cloudflare tunnel...")
                val tunnelResult = startCloudflaredWithToken(provisioned.runToken, port)
                
                if (tunnelResult != null) {
                    _serviceState.value = ServiceState.Error(tunnelResult)
                    updateNotification("Error: $tunnelResult")
                    ADBManager.disableTcpAdb()
                    stopSelf()
                    return@launch
                }
                
                val hostname = provisioned.hostname
                _tunnelUrl.value = hostname
                _serviceState.value = ServiceState.Running(hostname)
                settingsRepository.setLastTunnelUrl(hostname)
                updateNotification("Connected: $hostname")
                
            } catch (e: Exception) {
                val error = e.message ?: "Failed to provision tunnel"
                _serviceState.value = ServiceState.Error(error)
                updateNotification("Error: ${error.lineSequence().firstOrNull().orEmpty()}")
                ADBManager.disableTcpAdb()
                stopSelf()
            }
        }
    }
    
    private suspend fun startCloudflaredWithToken(token: String, port: Int): String? {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                // Get or download cloudflared binary
                val cloudflaredFile = getOrDownloadCloudflared()
                if (cloudflaredFile == null) {
                    return@withContext "Could not get cloudflared binary"
                }
                
                // Run: cloudflared tunnel run --token <token>
                val processBuilder = ProcessBuilder(
                    cloudflaredFile.absolutePath,
                    "tunnel",
                    "--no-autoupdate",
                    "run",
                    "--token", token
                )
                processBuilder.directory(filesDir)
                processBuilder.redirectErrorStream(true)
                
                cloudflaredProcess = processBuilder.start()
                
                // Wait a bit and check if it's still running
                kotlinx.coroutines.delay(3000)
                
                if (cloudflaredProcess?.isAlive != true) {
                    val output = cloudflaredProcess?.inputStream?.bufferedReader()?.readText() ?: ""
                    return@withContext "Cloudflared exited: ${output.take(200)}"
                }
                
                null // Success
            } catch (e: Exception) {
                e.message ?: "Failed to start cloudflared"
            }
        }
    }
    
    private fun getOrDownloadCloudflared(): java.io.File? {
        val internalFile = java.io.File(filesDir, "cloudflared")
        if (internalFile.exists() && internalFile.canExecute()) {
            return internalFile
        }
        
        // Try to extract from assets
        try {
            assets.open("cloudflared").use { input ->
                internalFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            internalFile.setExecutable(true)
            return internalFile
        } catch (e: Exception) {
            // Asset doesn't exist - would need to download
        }
        
        return null
    }
    
    private fun stopAdbTunnel() {
        serviceScope.launch {
            _serviceState.value = ServiceState.Stopping
            
            cloudflaredProcess?.destroy()
            cloudflaredProcess = null
            ADBManager.disableTcpAdb()
            
            _tunnelUrl.value = null
            _serviceState.value = ServiceState.Stopped
            
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(message: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = Intent(this, ADBService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Remote ADB")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun updateNotification(message: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(message))
    }
    
    companion object {
        const val ACTION_START = "com.remoteadb.ACTION_START"
        const val ACTION_STOP = "com.remoteadb.ACTION_STOP"
        const val CHANNEL_ID = "remote_adb_service"
        const val NOTIFICATION_ID = 1001
        
        fun startService(context: Context) {
            val intent = Intent(context, ADBService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, ADBService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}

sealed class ServiceState {
    object Stopped : ServiceState()
    object Starting : ServiceState()
    data class Running(val url: String) : ServiceState()
    object Stopping : ServiceState()
    data class Error(val message: String) : ServiceState()
}
