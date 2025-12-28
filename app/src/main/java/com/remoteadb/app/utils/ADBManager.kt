package com.remoteadb.app.utils

object ADBManager {
    
    suspend fun enableTcpAdb(port: Int = 5555): Boolean {
        val result = ShellExecutor.executeAsRoot("setprop service.adb.tcp.port $port && stop adbd && start adbd")
        return result.success
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
