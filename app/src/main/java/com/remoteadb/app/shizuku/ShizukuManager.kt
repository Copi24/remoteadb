package com.remoteadb.app.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

/**
 * Manages Shizuku connection for non-root ADB shell access.
 * Uses UserService to execute shell commands with ADB-level privileges.
 */
object ShizukuManager {
    
    private const val TAG = "ShizukuManager"
    
    sealed class ShizukuState {
        object NotInstalled : ShizukuState()
        object NotRunning : ShizukuState()
        object NoPermission : ShizukuState()
        object Connecting : ShizukuState()
        object Ready : ShizukuState()
    }
    
    private val _state = MutableStateFlow<ShizukuState>(ShizukuState.NotRunning)
    val state: StateFlow<ShizukuState> = _state
    
    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable
    
    private var remoteService: IRemoteAdbService? = null
    private var packageName: String = "com.remoteadb.app"
    private var versionCode: Int = 1
    
    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(packageName, RemoteAdbUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("adb_service")
            .debuggable(true)
            .version(versionCode)
    }
    
    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.i(TAG, "UserService connected: $name")
            if (binder != null && binder.pingBinder()) {
                remoteService = IRemoteAdbService.Stub.asInterface(binder)
                _state.value = ShizukuState.Ready
                _isAvailable.value = true
                
                // Verify we have elevated privileges
                try {
                    val uid = remoteService?.uid ?: -1
                    val pid = remoteService?.pid ?: -1
                    Log.i(TAG, "Service running with UID=$uid, PID=$pid")
                    // UID 2000 is shell (ADB privileges)
                    if (uid == 2000) {
                        Log.i(TAG, "Service has shell (ADB) privileges!")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to verify service", e)
                }
            } else {
                Log.e(TAG, "Invalid binder received")
                _state.value = ShizukuState.NoPermission
                _isAvailable.value = false
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "UserService disconnected: $name")
            remoteService = null
            _state.value = ShizukuState.NotRunning
            _isAvailable.value = false
        }
    }
    
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.i(TAG, "Shizuku binder received")
        checkPermission()
    }
    
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku binder dead")
        remoteService = null
        _state.value = ShizukuState.NotRunning
        _isAvailable.value = false
    }
    
    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission granted, binding service")
            bindUserService()
        } else {
            Log.w(TAG, "Permission denied")
            _state.value = ShizukuState.NoPermission
            _isAvailable.value = false
        }
    }
    
    fun init(packageName: String, versionCode: Int) {
        this.packageName = packageName
        this.versionCode = versionCode
        
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
    }
    
    fun destroy() {
        unbindUserService()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }
    
    private fun checkPermission() {
        if (Shizuku.isPreV11()) {
            Log.w(TAG, "Shizuku pre-v11 not supported")
            _state.value = ShizukuState.NotRunning
            return
        }
        
        when {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                Log.i(TAG, "Permission already granted")
                bindUserService()
            }
            Shizuku.shouldShowRequestPermissionRationale() -> {
                Log.w(TAG, "Permission denied previously")
                _state.value = ShizukuState.NoPermission
            }
            else -> {
                Log.i(TAG, "Requesting permission")
                _state.value = ShizukuState.NoPermission
            }
        }
    }
    
    fun requestPermission() {
        if (Shizuku.pingBinder()) {
            Shizuku.requestPermission(0)
        } else {
            _state.value = ShizukuState.NotRunning
        }
    }
    
    private fun bindUserService() {
        if (Shizuku.getVersion() < 10) {
            Log.e(TAG, "Shizuku version too old, requires API 10+")
            _state.value = ShizukuState.NotRunning
            return
        }
        
        try {
            _state.value = ShizukuState.Connecting
            Shizuku.bindUserService(userServiceArgs, userServiceConnection)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind UserService", e)
            _state.value = ShizukuState.NotRunning
        }
    }
    
    private fun unbindUserService() {
        try {
            if (remoteService != null) {
                Shizuku.unbindUserService(userServiceArgs, userServiceConnection, true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unbind UserService", e)
        }
        remoteService = null
    }
    
    /**
     * Execute a shell command with ADB privileges via UserService.
     */
    fun executeCommand(command: String): CommandResult {
        val service = remoteService
        if (service == null) {
            return CommandResult("", "Service not connected", -1)
        }
        
        return try {
            val output = service.execCommand(command)
            val exitCode = if (output.startsWith("ERROR:")) -1 else 0
            CommandResult(output, "", exitCode)
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed", e)
            CommandResult("", "Error: ${e.message}", -1)
        }
    }
    
    /**
     * Execute command and get exit code.
     */
    fun executeCommandWithExitCode(command: String): Int {
        val service = remoteService ?: return -1
        return try {
            service.execCommandWithExitCode(command)
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed", e)
            -1
        }
    }
    
    /**
     * Read file contents.
     */
    fun readFile(path: String): ByteArray? {
        val service = remoteService ?: return null
        return try {
            val data = service.readFile(path)
            if (data.isEmpty()) null else data
        } catch (e: Exception) {
            Log.e(TAG, "File read failed", e)
            null
        }
    }
    
    /**
     * Write file contents.
     */
    fun writeFile(path: String, data: ByteArray): Boolean {
        val service = remoteService ?: return false
        return try {
            service.writeFile(path, data)
        } catch (e: Exception) {
            Log.e(TAG, "File write failed", e)
            false
        }
    }
    
    /**
     * List directory contents.
     */
    fun listDirectory(path: String): List<String>? {
        val service = remoteService ?: return null
        return try {
            service.listDirectory(path)?.toList()
        } catch (e: Exception) {
            Log.e(TAG, "Directory listing failed", e)
            null
        }
    }
    
    /**
     * Check if path exists.
     */
    fun exists(path: String): Boolean {
        val service = remoteService ?: return false
        return try {
            service.exists(path)
        } catch (e: Exception) {
            Log.e(TAG, "Existence check failed", e)
            false
        }
    }
    
    /**
     * Delete file or directory.
     */
    fun delete(path: String): Boolean {
        val service = remoteService ?: return false
        return try {
            service.delete(path)
        } catch (e: Exception) {
            Log.e(TAG, "Delete failed", e)
            false
        }
    }
    
    /**
     * Check if Shizuku is installed on the device.
     */
    fun isShizukuInstalled(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }
    
    data class CommandResult(
        val stdout: String,
        val stderr: String,
        val exitCode: Int
    )
}
