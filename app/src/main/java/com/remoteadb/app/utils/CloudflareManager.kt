package com.remoteadb.app.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cloudflare Tunnel Manager
 * Uses cloudflared to create TCP tunnels - FREE and no account required!
 * 
 * Key benefits over Ngrok:
 * - 100% free, no payment required
 * - No account or signup needed
 * - Unlimited usage
 */
class CloudflareManager(private val context: Context) {
    
    private var cloudflaredProcess: Process? = null
    private var tunnelUrl: String? = null
    private var lastError: String? = null
    private var processOutput = StringBuilder()
    
    /**
     * Check if cloudflared binary is available and ready to use
     */
    fun isCloudflaredAvailable(): Boolean {
        val binary = getCloudflaredBinary()
        return binary != null && binary.exists() && binary.canExecute()
    }
    
    suspend fun startTunnel(port: Int = 5555): TunnelResult = withContext(Dispatchers.IO) {
        try {
            lastError = null
            processOutput.clear()
            
            // Check if cloudflared binary exists
            val cloudflaredFile = getCloudflaredBinary()
            if (cloudflaredFile == null || !cloudflaredFile.exists()) {
                return@withContext TunnelResult.Error(
                    "Cloudflared is not available.\n\nTip: For ADB (TCP), prefer Manual mode and run your tunnel/VPN externally (Termux/PC)."
                )
            }
            
            if (!cloudflaredFile.canExecute()) {
                cloudflaredFile.setExecutable(true)
                if (!cloudflaredFile.canExecute()) {
                    return@withContext TunnelResult.Error(
                        "Cannot execute cloudflared binary.\n\nTip: Switch to Manual mode and run your tunnel/VPN externally (Termux/PC)."
                    )
                }
            }
            
            // Start cloudflared tunnel
            // Using --url for quick tunnel (no account needed)
            val processBuilder = ProcessBuilder(
                cloudflaredFile.absolutePath,
                "tunnel",
                "--url", "tcp://localhost:$port",
                "--no-autoupdate"
            )
            processBuilder.directory(context.filesDir)
            processBuilder.redirectErrorStream(true)
            
            cloudflaredProcess = processBuilder.start()
            
            // Read output in background to capture the tunnel URL
            val outputReader = cloudflaredProcess!!.inputStream.bufferedReader()
            
            var foundUrl: String? = null
            val urlPatterns = listOf(
                """https://[a-zA-Z0-9-]+\.trycloudflare\.com""".toRegex(),
                """Your quick Tunnel has been created! Visit it at \(https://[^)]+\)""".toRegex()
            )
            
            // Read output with timeout
            val startTime = System.currentTimeMillis()
            val timeout = 30000L // 30 seconds
            
            Thread {
                try {
                    var line: String?
                    while (outputReader.readLine().also { line = it } != null) {
                        processOutput.append(line).append("\n")
                        line?.let { l ->
                            // Look for the tunnel URL
                            for (pattern in urlPatterns) {
                                val match = pattern.find(l)
                                if (match != null) {
                                    var url = match.value
                                    // Clean up the URL
                                    if (url.startsWith("Your quick")) {
                                        val httpMatch = """https://[a-zA-Z0-9-]+\.trycloudflare\.com""".toRegex().find(url)
                                        url = httpMatch?.value ?: url
                                    }
                                    tunnelUrl = url
                                    foundUrl = url
                                }
                            }
                            
                            // Check for errors
                            if (l.contains("error") || l.contains("ERR") || l.contains("failed")) {
                                if (l.contains("connection refused")) {
                                    lastError = "Connection refused - is ADB listening on port $port?"
                                } else if (l.contains("already in use")) {
                                    lastError = "Port already in use"
                                } else {
                                    lastError = l.take(100)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Reader closed
                }
            }.start()
            
            // Wait for tunnel URL with timeout
            while (foundUrl == null && System.currentTimeMillis() - startTime < timeout) {
                // Check if process died
                if (cloudflaredProcess?.isAlive != true) {
                    val error = lastError ?: processOutput.toString().take(200).ifEmpty { 
                        "Cloudflared process terminated unexpectedly" 
                    }
                    return@withContext TunnelResult.Error(error)
                }
                delay(500)
                foundUrl = tunnelUrl
            }
            
            if (foundUrl != null) {
                TunnelResult.Success(foundUrl!!)
            } else {
                val outputTail = processOutput.toString().lines().takeLast(12).joinToString("\n").trim()
                val hint = "Note: Cloudflare quick tunnels are mainly for HTTP; ADB needs raw TCP, so this may never produce a usable endpoint. Try Manual mode."
                val base = lastError ?: "Timeout waiting for tunnel URL."
                val msg = buildString {
                    append(base)
                    append("\n\n")
                    append(hint)
                    if (outputTail.isNotBlank()) {
                        append("\n\nLast logs:\n")
                        append(outputTail)
                    }
                }
                TunnelResult.Error(msg)
            }
        } catch (e: Exception) {
            TunnelResult.Error("Error: ${e.message ?: "Unknown error starting cloudflare tunnel"}")
        }
    }
    
    private fun getCloudflaredBinary(): File? {
        // Check in app's files directory first
        val internalFile = File(context.filesDir, "cloudflared")
        if (internalFile.exists()) {
            return internalFile
        }
        
        // Check in assets and extract if exists
        try {
            context.assets.open("cloudflared").use { input ->
                internalFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            internalFile.setExecutable(true)
            return internalFile
        } catch (e: Exception) {
            // Asset doesn't exist
        }
        
        return null
    }
    
    fun stopTunnel() {
        cloudflaredProcess?.destroy()
        cloudflaredProcess = null
        tunnelUrl = null
    }
    
    fun isRunning(): Boolean = cloudflaredProcess?.isAlive == true
    
    fun getTunnelUrl(): String? = tunnelUrl
    
    companion object {
        /**
         * Download cloudflared binary for the current architecture
         */
        suspend fun downloadCloudflared(context: Context, onProgress: (Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
            try {
                val arch = System.getProperty("os.arch") ?: ""
                val downloadUrl = when {
                    arch.contains("aarch64") || arch.contains("arm64") -> 
                        "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm64"
                    arch.contains("arm") -> 
                        "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm"
                    arch.contains("x86_64") || arch.contains("amd64") -> 
                        "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64"
                    else -> 
                        "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm64"
                }
                
                val url = URL(downloadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 30000
                connection.readTimeout = 60000
                connection.instanceFollowRedirects = true
                
                val fileSize = connection.contentLength
                val outputFile = File(context.filesDir, "cloudflared")
                
                connection.inputStream.use { input ->
                    outputFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            if (fileSize > 0) {
                                onProgress((totalBytesRead * 100 / fileSize).toInt())
                            }
                        }
                    }
                }
                
                outputFile.setExecutable(true)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
