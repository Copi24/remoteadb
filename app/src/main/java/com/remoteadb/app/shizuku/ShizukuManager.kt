package com.remoteadb.app.shizuku

import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

/**
 * Manages Shizuku connection for non-root ADB shell access.
 * Allows executing shell commands with ADB-level privileges without root.
 */
object ShizukuManager {
    
    sealed class ShizukuState {
        object NotInstalled : ShizukuState()
        object NotRunning : ShizukuState()
        object NoPermission : ShizukuState()
        object Ready : ShizukuState()
    }
    
    private val _state = MutableStateFlow<ShizukuState>(ShizukuState.NotRunning)
    val state: StateFlow<ShizukuState> = _state
    
    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable
    
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkPermission()
    }
    
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _state.value = ShizukuState.NotRunning
        _isAvailable.value = false
    }
    
    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            _state.value = ShizukuState.Ready
            _isAvailable.value = true
        } else {
            _state.value = ShizukuState.NoPermission
            _isAvailable.value = false
        }
    }
    
    fun init() {
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        
        // Check initial state
        if (Shizuku.pingBinder()) {
            checkPermission()
        }
    }
    
    fun destroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }
    
    private fun checkPermission() {
        if (Shizuku.isPreV11()) {
            _state.value = ShizukuState.NotRunning
            return
        }
        
        when {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                _state.value = ShizukuState.Ready
                _isAvailable.value = true
            }
            Shizuku.shouldShowRequestPermissionRationale() -> {
                _state.value = ShizukuState.NoPermission
            }
            else -> {
                _state.value = ShizukuState.NoPermission
            }
        }
    }
    
    fun requestPermission() {
        if (Shizuku.pingBinder()) {
            Shizuku.requestPermission(0)
        }
    }
    
    /**
     * Execute a shell command using Shizuku (ADB-level privileges).
     */
    fun executeCommand(command: String): CommandResult {
        if (_state.value != ShizukuState.Ready) {
            return CommandResult("", "Shizuku not ready", -1)
        }
        
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            CommandResult(stdout, stderr, exitCode)
        } catch (e: Exception) {
            CommandResult("", "Error: ${e.message}", -1)
        }
    }
    
    /**
     * Execute a streaming command (for logcat, etc.)
     */
    fun executeStreamingCommand(
        command: String,
        onOutput: (String) -> Unit,
        onError: (String) -> Unit,
        onComplete: (Int) -> Unit
    ): Process? {
        if (_state.value != ShizukuState.Ready) {
            onError("Shizuku not ready")
            onComplete(-1)
            return null
        }
        
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            
            Thread {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { onOutput(it) }
                }
            }.start()
            
            Thread {
                process.errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { onError(it) }
                }
            }.start()
            
            Thread {
                val exitCode = process.waitFor()
                onComplete(exitCode)
            }.start()
            
            process
        } catch (e: Exception) {
            onError("Error: ${e.message}")
            onComplete(-1)
            null
        }
    }
    
    data class CommandResult(
        val stdout: String,
        val stderr: String,
        val exitCode: Int
    )
}
