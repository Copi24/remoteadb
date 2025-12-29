package com.remoteadb.app.shizuku

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

/**
 * Remote server that listens for ADB-like commands and executes them via Shizuku.
 * This allows non-root devices to accept remote commands through the tunnel.
 * 
 * Protocol (JSON over TCP):
 * Request: {"type": "shell", "command": "ls -la"}
 * Response: {"success": true, "output": "...", "exitCode": 0}
 * 
 * Request: {"type": "push", "path": "/sdcard/file.txt", "data": "<base64>"}
 * Response: {"success": true}
 * 
 * Request: {"type": "pull", "path": "/sdcard/file.txt"}
 * Response: {"success": true, "data": "<base64>"}
 */
class ShizukuRemoteServer(
    private val port: Int = 5555
) {
    companion object {
        private const val TAG = "ShizukuRemoteServer"
    }
    
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning
    
    private val _connectedClients = MutableStateFlow(0)
    val connectedClients: StateFlow<Int> = _connectedClients
    
    fun start(): Boolean {
        if (_isRunning.value) {
            Log.w(TAG, "Server already running")
            return true
        }
        
        return try {
            serverSocket = ServerSocket(port)
            _isRunning.value = true
            Log.i(TAG, "Server started on port $port")
            
            serverJob = scope.launch {
                while (isActive && _isRunning.value) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        Log.i(TAG, "Client connected: ${clientSocket.inetAddress}")
                        _connectedClients.value++
                        
                        launch {
                            handleClient(clientSocket)
                        }
                    } catch (e: SocketException) {
                        if (_isRunning.value) {
                            Log.e(TAG, "Socket error", e)
                        }
                        break
                    }
                }
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
            false
        }
    }
    
    fun stop() {
        _isRunning.value = false
        serverJob?.cancel()
        serverSocket?.close()
        serverSocket = null
        _connectedClients.value = 0
        Log.i(TAG, "Server stopped")
    }
    
    private suspend fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream()), true)
            
            // Send welcome message
            writer.println(JSONObject().apply {
                put("type", "welcome")
                put("version", "1.0")
                put("mode", "shizuku")
            }.toString())
            
            while (socket.isConnected && _isRunning.value) {
                val line = reader.readLine() ?: break
                
                try {
                    val request = JSONObject(line)
                    val response = processRequest(request)
                    writer.println(response.toString())
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing request", e)
                    writer.println(JSONObject().apply {
                        put("success", false)
                        put("error", e.message ?: "Unknown error")
                    }.toString())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client handler error", e)
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                // Ignore
            }
            _connectedClients.value = (_connectedClients.value - 1).coerceAtLeast(0)
            Log.i(TAG, "Client disconnected")
        }
    }
    
    private fun processRequest(request: JSONObject): JSONObject {
        val type = request.optString("type", "")
        
        return when (type) {
            "shell" -> handleShellCommand(request)
            "push" -> handlePush(request)
            "pull" -> handlePull(request)
            "list" -> handleList(request)
            "exists" -> handleExists(request)
            "delete" -> handleDelete(request)
            "ping" -> JSONObject().apply {
                put("success", true)
                put("pong", System.currentTimeMillis())
            }
            else -> JSONObject().apply {
                put("success", false)
                put("error", "Unknown command type: $type")
            }
        }
    }
    
    private fun handleShellCommand(request: JSONObject): JSONObject {
        val command = request.optString("command", "")
        if (command.isEmpty()) {
            return JSONObject().apply {
                put("success", false)
                put("error", "No command provided")
            }
        }
        
        val result = ShizukuManager.executeCommand(command)
        return JSONObject().apply {
            put("success", result.exitCode == 0)
            put("output", result.stdout)
            put("stderr", result.stderr)
            put("exitCode", result.exitCode)
        }
    }
    
    private fun handlePush(request: JSONObject): JSONObject {
        val path = request.optString("path", "")
        val dataBase64 = request.optString("data", "")
        
        if (path.isEmpty() || dataBase64.isEmpty()) {
            return JSONObject().apply {
                put("success", false)
                put("error", "Path and data required")
            }
        }
        
        return try {
            val data = Base64.decode(dataBase64, Base64.DEFAULT)
            val success = ShizukuManager.writeFile(path, data)
            JSONObject().apply {
                put("success", success)
                if (!success) put("error", "Failed to write file")
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("success", false)
                put("error", e.message)
            }
        }
    }
    
    private fun handlePull(request: JSONObject): JSONObject {
        val path = request.optString("path", "")
        
        if (path.isEmpty()) {
            return JSONObject().apply {
                put("success", false)
                put("error", "Path required")
            }
        }
        
        val data = ShizukuManager.readFile(path)
        return if (data != null) {
            JSONObject().apply {
                put("success", true)
                put("data", Base64.encodeToString(data, Base64.DEFAULT))
                put("size", data.size)
            }
        } else {
            JSONObject().apply {
                put("success", false)
                put("error", "Failed to read file or file not found")
            }
        }
    }
    
    private fun handleList(request: JSONObject): JSONObject {
        val path = request.optString("path", "")
        
        if (path.isEmpty()) {
            return JSONObject().apply {
                put("success", false)
                put("error", "Path required")
            }
        }
        
        val files = ShizukuManager.listDirectory(path)
        return if (files != null) {
            JSONObject().apply {
                put("success", true)
                put("files", files)
            }
        } else {
            JSONObject().apply {
                put("success", false)
                put("error", "Failed to list directory")
            }
        }
    }
    
    private fun handleExists(request: JSONObject): JSONObject {
        val path = request.optString("path", "")
        
        if (path.isEmpty()) {
            return JSONObject().apply {
                put("success", false)
                put("error", "Path required")
            }
        }
        
        return JSONObject().apply {
            put("success", true)
            put("exists", ShizukuManager.exists(path))
        }
    }
    
    private fun handleDelete(request: JSONObject): JSONObject {
        val path = request.optString("path", "")
        
        if (path.isEmpty()) {
            return JSONObject().apply {
                put("success", false)
                put("error", "Path required")
            }
        }
        
        val success = ShizukuManager.delete(path)
        return JSONObject().apply {
            put("success", success)
            if (!success) put("error", "Failed to delete")
        }
    }
}
