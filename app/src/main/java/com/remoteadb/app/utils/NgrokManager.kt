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
    private var ngrokOutput = StringBuilder()
    
    suspend fun startTunnel(authToken: String, port: Int = 5555): TunnelResult = withContext(Dispatchers.IO) {
        try {
            lastError = null
            ngrokOutput.clear()
            
            // Extract ngrok binary
            val ngrokFile = ShellExecutor.extractNgrokBinary(context)
            
            // Check if ngrok binary exists and is executable
            if (!ngrokFile.exists()) {
                return@withContext TunnelResult.Error("Ngrok binary not found. Please reinstall the app.")
            }
            if (!ngrokFile.canExecute()) {
                ngrokFile.setExecutable(true)
                if (!ngrokFile.canExecute()) {
                    return@withContext TunnelResult.Error("Cannot execute ngrok binary. Check app permissions.")
                }
            }
            
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
            
            // Check if port is accessible by verifying ADB is listening
            val portCheck = checkPortListening(port)
            if (!portCheck.first) {
                return@withContext TunnelResult.Error("ADB not listening on port $port. ${portCheck.second}")
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
            
            // Read output in background
            Thread {
                try {
                    var line: String?
                    while (outputReader.readLine().also { line = it } != null) {
                        ngrokOutput.append(line).append("\n")
                        // Check for error patterns in ngrok output
                        line?.let { l ->
                            parseNgrokLogLine(l)
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
                val error = buildErrorMessage("Ngrok process terminated unexpectedly")
                return@withContext TunnelResult.Error(error)
            }
            
            // Try to get tunnel URL from API
            var lastApiError: String? = null
            repeat(10) { attempt ->
                try {
                    val url = URL("http://127.0.0.1:4040/api/tunnels")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 2000
                    connection.readTimeout = 2000
                    
                    val responseCode = connection.responseCode
                    if (responseCode != 200) {
                        lastApiError = "Ngrok API returned status $responseCode"
                        delay(1000)
                        return@repeat
                    }
                    
                    val response = connection.inputStream.bufferedReader().readText()
                    
                    // Parse JSON response to get public_url
                    val publicUrlRegex = """"public_url"\s*:\s*"(tcp://[^"]+)"""".toRegex()
                    val match = publicUrlRegex.find(response)
                    
                    if (match != null) {
                        tunnelUrl = match.groupValues[1]
                        return@withContext TunnelResult.Success(tunnelUrl!!)
                    } else {
                        // Check if tunnels array is empty
                        if (response.contains("\"tunnels\":[]") || response.contains("\"tunnels\": []")) {
                            lastApiError = "No tunnels created yet"
                        } else {
                            lastApiError = "Could not parse tunnel URL from response"
                        }
                    }
                } catch (e: java.net.ConnectException) {
                    lastApiError = "Cannot connect to ngrok API (port 4040)"
                    // Check if ngrok process is still alive
                    if (ngrokProcess?.isAlive != true) {
                        val error = buildErrorMessage("Ngrok process died")
                        return@withContext TunnelResult.Error(error)
                    }
                    delay(1000)
                } catch (e: Exception) {
                    lastApiError = "API error: ${e.message}"
                    // Check if ngrok process is still alive
                    if (ngrokProcess?.isAlive != true) {
                        val error = buildErrorMessage("Ngrok process died")
                        return@withContext TunnelResult.Error(error)
                    }
                    delay(1000)
                }
            }
            
            // If we get here, couldn't get tunnel URL
            val error = buildErrorMessage(lastApiError ?: "Failed to establish tunnel")
            TunnelResult.Error(error)
        } catch (e: Exception) {
            TunnelResult.Error("Error: ${e.message ?: "Unknown error starting tunnel"}")
        }
    }
    
    private fun parseNgrokLogLine(line: String) {
        // Check for error patterns in ngrok output
        if (line.contains("err") || line.contains("ERR") || line.contains("error") || line.contains("failed")) {
            // Extract error message from JSON log
            val errMsgRegex = """"msg"\s*:\s*"([^"]+)"""".toRegex()
            val errMatch = errMsgRegex.find(line)
            if (errMatch != null) {
                val msg = errMatch.groupValues[1]
                if (msg.isNotBlank() && !msg.contains("starting")) {
                    lastError = msg
                }
            }
            // Also check for "err" field
            val errFieldRegex = """"err"\s*:\s*"([^"]+)"""".toRegex()
            val errFieldMatch = errFieldRegex.find(line)
            if (errFieldMatch != null) {
                lastError = errFieldMatch.groupValues[1]
            }
            // Check for common error patterns
            if (line.contains("authentication failed") || line.contains("invalid auth")) {
                lastError = "Invalid ngrok auth token"
            }
            if (line.contains("tunnel session failed") || line.contains("session closed")) {
                lastError = "Tunnel session failed - check internet connection"
            }
            if (line.contains("tcp tunnel") && line.contains("not available")) {
                lastError = "TCP tunnels require a paid ngrok plan"
            }
            if (line.contains("ERR_NGROK_")) {
                // Extract ngrok error code
                val errorCodeRegex = """ERR_NGROK_\d+""".toRegex()
                val codeMatch = errorCodeRegex.find(line)
                if (codeMatch != null) {
                    lastError = "${lastError ?: "Error"} (${codeMatch.value})"
                }
            }
        }
    }
    
    private fun buildErrorMessage(defaultMsg: String): String {
        return when {
            lastError != null -> lastError!!
            ngrokOutput.isNotBlank() -> {
                // Try to extract meaningful info from output
                val output = ngrokOutput.toString()
                when {
                    output.contains("ERR_NGROK_105") -> "Invalid ngrok auth token"
                    output.contains("ERR_NGROK_108") -> "TCP tunnels require ngrok paid plan"
                    output.contains("ERR_NGROK_120") -> "Ngrok account rate limited"
                    output.contains("ERR_NGROK_") -> {
                        val errorCodeRegex = """ERR_NGROK_\d+""".toRegex()
                        val match = errorCodeRegex.find(output)
                        match?.value ?: defaultMsg
                    }
                    output.contains("authentication") -> "Authentication failed - check auth token"
                    output.contains("tcp") && output.contains("not available") -> "TCP tunnels require paid ngrok plan"
                    else -> defaultMsg
                }
            }
            else -> defaultMsg
        }
    }
    
    private suspend fun checkPortListening(port: Int): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // Check if ADB TCP port is configured
            val portResult = ShellExecutor.executeAsRoot("getprop service.adb.tcp.port")
            val configuredPort = portResult.output.trim().toIntOrNull() ?: -1
            
            if (configuredPort != port) {
                return@withContext Pair(false, "ADB TCP port is $configuredPort, expected $port")
            }
            
            // Check if adbd is running
            val adbdResult = ShellExecutor.execute("ps -A | grep adbd")
            if (!adbdResult.output.contains("adbd")) {
                return@withContext Pair(false, "ADB daemon not running")
            }
            
            // Try to check if port is listening
            val netstatResult = ShellExecutor.execute("netstat -tlnp 2>/dev/null | grep :$port || ss -tlnp 2>/dev/null | grep :$port")
            if (netstatResult.output.isBlank()) {
                // Port might still be starting, give it some time
                return@withContext Pair(true, "Port may be starting")
            }
            
            Pair(true, "OK")
        } catch (e: Exception) {
            Pair(true, "Could not verify port: ${e.message}")
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
