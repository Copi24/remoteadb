package com.remoteadb.app.utils

import com.remoteadb.app.shizuku.ShizukuManager

/**
 * Execution mode for ADB commands.
 */
enum class ExecutionMode {
    ROOT,      // Use su for root access
    SHIZUKU,   // Use Shizuku for ADB-level shell access
    NONE       // No elevated access available
}

object ADBManager {
    
    data class AdbResult(
        val success: Boolean,
        val error: String? = null
    )
    
    /**
     * Detect the best available execution mode.
     */
    suspend fun detectExecutionMode(): ExecutionMode {
        // First try root
        if (ShellExecutor.checkRootAccess()) {
            return ExecutionMode.ROOT
        }
        
        // Then try Shizuku
        if (ShizukuManager.isAvailable.value) {
            return ExecutionMode.SHIZUKU
        }
        
        return ExecutionMode.NONE
    }
    
    /**
     * Check if we have any elevated access (root or Shizuku).
     */
    suspend fun hasElevatedAccess(): Boolean {
        return detectExecutionMode() != ExecutionMode.NONE
    }
    
    suspend fun enableTcpAdb(port: Int = 5555): AdbResult {
        val mode = detectExecutionMode()
        
        return when (mode) {
            ExecutionMode.ROOT -> enableTcpAdbWithRoot(port)
            ExecutionMode.SHIZUKU -> enableTcpAdbWithShizuku(port)
            ExecutionMode.NONE -> AdbResult(
                false, 
                "No elevated access available.\n\n" +
                "Either:\n" +
                "• Grant ROOT access (Magisk/KernelSU), or\n" +
                "• Install Shizuku from Play Store and enable it"
            )
        }
    }
    
    private suspend fun enableTcpAdbWithRoot(port: Int): AdbResult {
        // Set the TCP port
        val setPortResult = ShellExecutor.executeAsRoot("setprop service.adb.tcp.port $port")
        if (!setPortResult.success) {
            return AdbResult(false, "Failed to set ADB port: ${setPortResult.error}")
        }
        
        // Restart adbd
        val stopResult = ShellExecutor.executeAsRoot("stop adbd")
        if (!stopResult.success) {
            return AdbResult(false, "Failed to stop adbd: ${stopResult.error}")
        }
        
        // Small delay to ensure adbd stops
        kotlinx.coroutines.delay(500)
        
        val startResult = ShellExecutor.executeAsRoot("start adbd")
        if (!startResult.success) {
            return AdbResult(false, "Failed to start adbd: ${startResult.error}")
        }
        
        // Wait for adbd to start and verify
        kotlinx.coroutines.delay(1000)
        
        // Verify ADB is now listening on TCP
        val verifyResult = ShellExecutor.executeAsRoot("getprop service.adb.tcp.port")
        val actualPort = verifyResult.output.trim().toIntOrNull() ?: -1
        
        if (actualPort != port) {
            return AdbResult(false, "ADB port not set correctly. Expected $port, got $actualPort")
        }
        
        return AdbResult(true)
    }
    
    private fun enableTcpAdbWithShizuku(port: Int): AdbResult {
        // Use Shizuku to execute commands with ADB-level (shell) privileges
        val setPort = ShizukuManager.executeCommand("setprop service.adb.tcp.port $port")
        if (setPort.exitCode != 0) {
            return AdbResult(false, "Failed to set ADB port via Shizuku: ${setPort.stderr}")
        }
        
        // Restart adbd
        val stop = ShizukuManager.executeCommand("stop adbd")
        if (stop.exitCode != 0) {
            return AdbResult(false, "Failed to stop adbd via Shizuku: ${stop.stderr}")
        }
        
        Thread.sleep(500)
        
        val start = ShizukuManager.executeCommand("start adbd")
        if (start.exitCode != 0) {
            return AdbResult(false, "Failed to start adbd via Shizuku: ${start.stderr}")
        }
        
        Thread.sleep(1000)
        
        // Verify
        val verify = ShizukuManager.executeCommand("getprop service.adb.tcp.port")
        val actualPort = verify.stdout.trim().toIntOrNull() ?: -1
        
        if (actualPort != port) {
            return AdbResult(false, "ADB port not set correctly. Expected $port, got $actualPort")
        }
        
        return AdbResult(true)
    }
    
    suspend fun disableTcpAdb(): Boolean {
        val mode = detectExecutionMode()
        return when (mode) {
            ExecutionMode.ROOT -> {
                ShellExecutor.executeAsRoot("setprop service.adb.tcp.port -1 && stop adbd && start adbd").success
            }
            ExecutionMode.SHIZUKU -> {
                ShizukuManager.executeCommand("setprop service.adb.tcp.port -1").exitCode == 0 &&
                ShizukuManager.executeCommand("stop adbd").exitCode == 0 &&
                ShizukuManager.executeCommand("start adbd").exitCode == 0
            }
            ExecutionMode.NONE -> false
        }
    }
    
    suspend fun isAdbTcpEnabled(): Boolean {
        val result = ShellExecutor.execute("getprop service.adb.tcp.port")
        return result.success && result.output.trim().toIntOrNull()?.let { it > 0 } ?: false
    }
    
    suspend fun getAdbTcpPort(): Int {
        val result = ShellExecutor.execute("getprop service.adb.tcp.port")
        return result.output.trim().toIntOrNull() ?: -1
    }
    
    suspend fun getDeviceInfo(): DeviceInfo {
        val model = ShellExecutor.execute("getprop ro.product.model").output.trim()
        val android = ShellExecutor.execute("getprop ro.build.version.release").output.trim()
        val sdk = ShellExecutor.execute("getprop ro.build.version.sdk").output.trim()
        
        return DeviceInfo(
            model = model.ifEmpty { "Unknown" },
            androidVersion = android.ifEmpty { "Unknown" },
            sdkVersion = sdk.ifEmpty { "Unknown" }
        )
    }
    
    suspend fun getLocalIpAddress(): String {
        val result = ShellExecutor.execute("ip route get 1 | awk '{print \$7;exit}'")
        return result.output.trim().ifEmpty { 
            ShellExecutor.execute("hostname -I | awk '{print \$1}'").output.trim()
        }
    }
}

data class DeviceInfo(
    val model: String,
    val androidVersion: String,
    val sdkVersion: String
)
