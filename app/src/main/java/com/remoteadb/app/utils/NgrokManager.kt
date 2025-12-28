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
    private var lastError: String? = null
    
    suspend fun startTunnel(authToken: String, port: Int = 5555): TunnelResult = withContext(Dispatchers.IO) {
        try {
            lastError = null
            
            // Extract ngrok binary
            val ngrokFile = ShellExecutor.extractNgrokBinary(context)
            
            // Configure ngrok with auth token
            val configProcess = Runtime.getRuntime().exec(
                arrayOf(ngrokFile.absolutePath, "config", "add-authtoken", authToken)
            )
            val configOutput = configProcess.inputStream.bufferedReader().readText()
            val configError = configProcess.errorStream.bufferedReader().readText()
            val configResult = configProcess.waitFor()
            
            if (configResult != 0) {
                val errorMsg = configError.ifEmpty { configOutput }.ifEmpty { "Unknown error" }
                return@withContext TunnelResult.Error("Failed to configure ngrok: $errorMsg")
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
            
            // Start a thread to capture ngrok output for error messages
            val outputReader = ngrokProcess!!.inputStream.bufferedReader()
            val outputBuilder = StringBuilder()
            
            // Read output in background
            Thread {
                try {
                    var line: String?
                    while (outputReader.readLine().also { line = it } != null) {
                        outputBuilder.append(line).append("\n")
                        // Check for error patterns in ngrok output
                        line?.let { l ->
                            if (l.contains("err") || l.contains("ERR") || l.contains("error")) {
                                // Extract error message from JSON log
                                val errMsgRegex = """"msg"\s*:\s*"([^"]+)"""".toRegex()
                                val errMatch = errMsgRegex.find(l)
                                if (errMatch != null) {
                                    lastError = errMatch.groupValues[1]
                                }
                                // Also check for "err" field
                                val errFieldRegex = """"err"\s*:\s*"([^"]+)"""".toRegex()
                                val errFieldMatch = errFieldRegex.find(l)
                                if (errFieldMatch != null) {
                                    lastError = errFieldMatch.groupValues[1]
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Reader closed
                }
            }.start()
            
            // Wait for tunnel to establish and get URL
            delay(3000) // Give ngrok time to start
            
            // Check if process died early
            if (ngrokProcess?.isAlive != true) {
                val error = lastError ?: outputBuilder.toString().take(200).ifEmpty { "Ngrok process terminated unexpectedly" }
                return@withContext TunnelResult.Error(error)
            }
            
            // Try to get tunnel URL from API
            repeat(10) { attempt ->
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
                    // Check if ngrok process is still alive
                    if (ngrokProcess?.isAlive != true) {
                        val error = lastError ?: "Ngrok process died unexpectedly"
                        return@withContext TunnelResult.Error(error)
                    }
                    delay(1000)
                }
            }
            
            // If we get here, couldn't get tunnel URL
            val error = lastError ?: "Failed to establish tunnel - check auth token and network"
            TunnelResult.Error(error)
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
