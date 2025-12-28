package com.remoteadb.app.utils

object ADBManager {
    
    data class AdbResult(
        val success: Boolean,
        val error: String? = null
    )
    
    suspend fun enableTcpAdb(port: Int = 5555): AdbResult {
        // First check if we have root access
        val rootCheck = ShellExecutor.checkRootAccess()
        if (!rootCheck) {
            return AdbResult(false, "Root access denied. Please grant root permission.")
        }
        
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
    
    suspend fun disableTcpAdb(): Boolean {
        val result = ShellExecutor.executeAsRoot("setprop service.adb.tcp.port -1 && stop adbd && start adbd")
        return result.success
    }
    
    suspend fun isAdbTcpEnabled(): Boolean {
        val result = ShellExecutor.executeAsRoot("getprop service.adb.tcp.port")
        return result.success && result.output.trim().toIntOrNull()?.let { it > 0 } ?: false
    }
    
    suspend fun getAdbTcpPort(): Int {
        val result = ShellExecutor.executeAsRoot("getprop service.adb.tcp.port")
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
