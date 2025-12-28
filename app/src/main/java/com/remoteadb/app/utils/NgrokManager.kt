package com.remoteadb.app.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class NgrokManager(private val context: Context) {
    
    private var ngrokProcess: Process? = null
    private var tunnelUrl: String? = null
    
    suspend fun startTunnel(authToken: String, port: Int = 5555): TunnelResult = withContext(Dispatchers.IO) {
        try {
            // Extract ngrok binary
            val ngrokFile = ShellExecutor.extractNgrokBinary(context)
            
            // Configure ngrok with auth token
            val configResult = Runtime.getRuntime().exec(
                arrayOf(ngrokFile.absolutePath, "config", "add-authtoken", authToken)
            ).waitFor()
            
            if (configResult != 0) {
                return@withContext TunnelResult.Error("Failed to configure ngrok auth token")
            }
            
            // Start ngrok tunnel
            val processBuilder = ProcessBuilder(
                ngrokFile.absolutePath,
                "tcp",
                port.toString(),
                "--log=stdout",
                "--log-format=json"
            )
            processBuilder.directory(context.filesDir)
            processBuilder.redirectErrorStream(true)
            
            ngrokProcess = processBuilder.start()
            
            // Wait for tunnel to establish and get URL
            delay(3000) // Give ngrok time to start
            
            // Try to get tunnel URL from API
            repeat(10) {
                try {
                    val url = URL("http://127.0.0.1:4040/api/tunnels")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 2000
                    connection.readTimeout = 2000
                    
                    val response = connection.inputStream.bufferedReader().readText()
                    
                    // Parse JSON response to get public_url
                    val publicUrlRegex = """"public_url"\s*:\s*"(tcp://[^"]+)"""".toRegex()
                    val match = publicUrlRegex.find(response)
                    
                    if (match != null) {
                        tunnelUrl = match.groupValues[1]
                        return@withContext TunnelResult.Success(tunnelUrl!!)
                    }
                } catch (e: Exception) {
                    delay(1000)
                }
            }
            
            TunnelResult.Error("Failed to get tunnel URL")
        } catch (e: Exception) {
            TunnelResult.Error(e.message ?: "Unknown error starting tunnel")
        }
    }
    
    fun stopTunnel() {
        ngrokProcess?.destroy()
        ngrokProcess = null
        tunnelUrl = null
    }
    
    fun isRunning(): Boolean = ngrokProcess?.isAlive == true
    
    fun getTunnelUrl(): String? = tunnelUrl
}

sealed class TunnelResult {
    data class Success(val url: String) : TunnelResult()
    data class Error(val message: String) : TunnelResult()
}
