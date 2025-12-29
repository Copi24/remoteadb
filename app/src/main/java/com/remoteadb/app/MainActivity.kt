package com.remoteadb.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.remoteadb.app.service.ADBService
import com.remoteadb.app.service.ServiceState
import com.remoteadb.app.ui.screens.HomeScreen
import com.remoteadb.app.ui.screens.OnboardingScreen
import com.remoteadb.app.ui.screens.SettingsScreen
import com.remoteadb.app.ui.theme.RemoteADBTheme
import com.remoteadb.app.utils.ADBManager
import com.remoteadb.app.utils.DeviceInfo
import com.remoteadb.app.utils.SettingsRepository
import com.remoteadb.app.utils.ShellExecutor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    private lateinit var settingsRepository: SettingsRepository
    private var adbService: ADBService? = null
    private var serviceBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ADBService.LocalBinder
            adbService = binder.getService()
            serviceBound = true
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            adbService = null
            serviceBound = false
        }
    }
    
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Handle result */ }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        settingsRepository = SettingsRepository(this)
        
        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        setContent {
            RemoteADBTheme {
                RemoteADBApp(settingsRepository)
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        Intent(this, ADBService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }
    
    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun RemoteADBApp(settingsRepository: SettingsRepository) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    
    // State
    var onboardingCompleted by remember { mutableStateOf<Boolean?>(null) }
    var adbPort by remember { mutableStateOf("5555") }
    var autoStartOnBoot by remember { mutableStateOf(false) }
    var managedCfHostname by remember { mutableStateOf("") }
    var managedCfRunToken by remember { mutableStateOf("") }
    var deviceInfo by remember { mutableStateOf<DeviceInfo?>(null) }
    var localIp by remember { mutableStateOf<String?>(null) }
    var hasRootAccess by remember { mutableStateOf(false) }
    var serviceState by remember { mutableStateOf<ServiceState>(ServiceState.Stopped) }
    var tunnelUrl by remember { mutableStateOf<String?>(null) }
    
    // Load initial settings
    LaunchedEffect(Unit) {
        onboardingCompleted = settingsRepository.onboardingCompleted.first()
        adbPort = settingsRepository.adbPort.first()
        autoStartOnBoot = settingsRepository.autoStartOnBoot.first()
        managedCfHostname = settingsRepository.managedCfHostname.first()
        managedCfRunToken = settingsRepository.managedCfRunToken.first()
        tunnelUrl = settingsRepository.lastTunnelUrl.first().takeIf { it.isNotEmpty() }
        
        // Load device info
        hasRootAccess = ShellExecutor.checkRootAccess()
        deviceInfo = ADBManager.getDeviceInfo()
        localIp = ADBManager.getLocalIpAddress()
    }
    
    // Collect settings changes
    LaunchedEffect(Unit) {
        settingsRepository.adbPort.collect { adbPort = it }
    }
    LaunchedEffect(Unit) {
        settingsRepository.autoStartOnBoot.collect { autoStartOnBoot = it }
    }
    LaunchedEffect(Unit) {
        settingsRepository.managedCfHostname.collect { managedCfHostname = it }
    }
    LaunchedEffect(Unit) {
        settingsRepository.managedCfRunToken.collect { managedCfRunToken = it }
    }
    
    // Determine start destination
    val startDestination = when (onboardingCompleted) {
        true -> "home"
        false -> "onboarding"
        null -> return // Still loading
    }
    
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.fillMaxSize(),
        enterTransition = {
            slideInHorizontally(initialOffsetX = { it }) + fadeIn()
        },
        exitTransition = {
            slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
        },
        popEnterTransition = {
            slideInHorizontally(initialOffsetX = { -it }) + fadeIn()
        },
        popExitTransition = {
            slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        }
    ) {
        composable("onboarding") {
            OnboardingScreen(
                onComplete = {
                    scope.launch {
                        settingsRepository.setOnboardingCompleted(true)
                    }
                    navController.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }
        
        composable("home") {
            val context = androidx.compose.ui.platform.LocalContext.current
            
            HomeScreen(
                serviceState = serviceState,
                tunnelUrl = tunnelUrl,
                deviceInfo = deviceInfo,
                localIp = localIp,
                adbPort = adbPort,
                hasRootAccess = hasRootAccess,
                onToggleService = {
                    if (serviceState is ServiceState.Running) {
                        ADBService.stopService(context)
                        serviceState = ServiceState.Stopping
                        scope.launch {
                            kotlinx.coroutines.delay(1500)
                            serviceState = ServiceState.Stopped
                            tunnelUrl = null
                        }
                    } else {
                        ADBService.startService(context)
                        serviceState = ServiceState.Starting
                        scope.launch {
                            // Poll for tunnel URL
                            repeat(30) { // Wait up to 30 seconds
                                kotlinx.coroutines.delay(1000)
                                val url = settingsRepository.lastTunnelUrl.first()
                                if (url.isNotEmpty()) {
                                    tunnelUrl = url
                                    serviceState = ServiceState.Running(url)
                                    return@launch
                                }
                            }
                            // Timeout
                            serviceState = ServiceState.Error("Timeout - check logs in notification")
                        }
                    }
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                }
            )
        }
        
        composable("settings") {
            SettingsScreen(
                adbPort = adbPort,
                autoStartOnBoot = autoStartOnBoot,
                onAdbPortChange = { port ->
                    scope.launch {
                        settingsRepository.setAdbPort(port)
                    }
                },
                onAutoStartChange = { enabled ->
                    scope.launch {
                        settingsRepository.setAutoStartOnBoot(enabled)
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
