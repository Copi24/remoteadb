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
import com.remoteadb.app.utils.CloudflareManager
import com.remoteadb.app.utils.NgrokManager
import com.remoteadb.app.utils.SettingsRepository
import com.remoteadb.app.utils.TunnelProvider
import com.remoteadb.app.utils.TunnelResult
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
    private lateinit var ngrokManager: NgrokManager
    private lateinit var cloudflareManager: CloudflareManager
    private lateinit var settingsRepository: SettingsRepository
    private var currentProvider: TunnelProvider = TunnelProvider.CLOUDFLARE
    
    private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.Stopped)
    val serviceState: StateFlow<ServiceState> = _serviceState
    
    private val _tunnelUrl = MutableStateFlow<String?>(null)
    val tunnelUrl: StateFlow<String?> = _tunnelUrl
    
    inner class LocalBinder : Binder() {
        fun getService(): ADBService = this@ADBService
    }
    
    override fun onCreate() {
        super.onCreate()
        ngrokManager = NgrokManager(this)
        cloudflareManager = CloudflareManager(this)
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
        ngrokManager.stopTunnel()
        cloudflareManager.stopTunnel()
    }
    
    private fun startAdbTunnel() {
        serviceScope.launch {
            _serviceState.value = ServiceState.Starting
            
            startForeground(NOTIFICATION_ID, createNotification("Starting ADB tunnel..."))
            
            // Get settings
            val provider = settingsRepository.tunnelProvider.first()
            currentProvider = provider
            val port = settingsRepository.adbPort.first().toIntOrNull() ?: 5555
            
            // Check provider-specific requirements
            if (provider == TunnelProvider.NGROK) {
                val authToken = settingsRepository.ngrokAuthToken.first()
                if (authToken.isEmpty()) {
                    _serviceState.value = ServiceState.Error("Ngrok auth token not configured. Go to Settings to add it.")
                    stopSelf()
                    return@launch
                }
            }
            
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
            
            // Start tunnel based on provider
            updateNotification("Starting ${provider.displayName} tunnel...")
            
            val result = when (provider) {
                TunnelProvider.CLOUDFLARE -> {
                    cloudflareManager.startTunnel(port)
                }
                TunnelProvider.NGROK -> {
                    val authToken = settingsRepository.ngrokAuthToken.first()
                    ngrokManager.startTunnel(authToken, port)
                }
            }
            
            when (result) {
                is TunnelResult.Success -> {
                    _tunnelUrl.value = result.url
                    _serviceState.value = ServiceState.Running(result.url)
                    settingsRepository.setLastTunnelUrl(result.url)
                    updateNotification("Connected: ${result.url}")
                }
                is TunnelResult.Error -> {
                    _serviceState.value = ServiceState.Error(result.message)
                    ADBManager.disableTcpAdb()
                    stopSelf()
                }
            }
        }
    }
    
    private fun stopAdbTunnel() {
        serviceScope.launch {
            _serviceState.value = ServiceState.Stopping
            
            // Stop the appropriate tunnel
            when (currentProvider) {
                TunnelProvider.CLOUDFLARE -> cloudflareManager.stopTunnel()
                TunnelProvider.NGROK -> ngrokManager.stopTunnel()
            }
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
