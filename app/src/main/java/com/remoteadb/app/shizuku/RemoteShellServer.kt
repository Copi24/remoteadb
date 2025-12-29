package com.remoteadb.app.shizuku

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * WebSocket server that accepts remote shell commands.
 * Protocol:
 * - {"type":"shell","cmd":"ls -la"} -> {"type":"output","stdout":"...","stderr":"...","exit":0}
 * - {"type":"stream","cmd":"logcat"} -> multiple {"type":"line","text":"..."}
 * - {"type":"push","path":"/sdcard/file.txt","data":"base64..."} -> {"type":"ok"}
 * - {"type":"pull","path":"/sdcard/file.txt"} -> {"type":"file","data":"base64..."}
 * - {"type":"stop","id":"..."} -> stops streaming command
 */
class RemoteShellServer(
    private val port: Int = 5556,
    private val useRoot: Boolean = false
) {
    companion object {
        private const val TAG = "RemoteShellServer"
    }
    
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val streamingProcesses = ConcurrentHashMap<String, Process>()
    
    var isRunning = false
        private set
    
    fun start(): Boolean {
        if (isRunning) return true
        
        return try {
            serverSocket = ServerSocket(port)
            isRunning = true
            
            serverJob = scope.launch {
                Log.i(TAG, "Server started on port $port")
                while (isActive && isRunning) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        launch { handleClient(client) }
                    } catch (e: Exception) {
                        if (isRunning) Log.e(TAG, "Accept error", e)
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
        isRunning = false
        streamingProcesses.values.forEach { it.destroy() }
        streamingProcesses.clear()
        serverJob?.cancel()
        serverSocket?.close()
        serverSocket = null
    }
    
    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            val input = socket.getInputStream().bufferedReader()
            val output = socket.getOutputStream().bufferedWriter()
            
            // WebSocket handshake
            val headers = mutableMapOf<String, String>()
            var line = input.readLine()
            while (line != null && line.isNotEmpty()) {
                if (line.contains(":")) {
                    val (key, value) = line.split(":", limit = 2)
                    headers[key.trim()] = value.trim()
                }
                line = input.readLine()
            }
            
            val wsKey = headers["Sec-WebSocket-Key"]
            if (wsKey != null) {
                // Complete WebSocket handshake
                val acceptKey = generateAcceptKey(wsKey)
                output.write("HTTP/1.1 101 Switching Protocols\r\n")
                output.write("Upgrade: websocket\r\n")
                output.write("Connection: Upgrade\r\n")
                output.write("Sec-WebSocket-Accept: $acceptKey\r\n")
                output.write("\r\n")
                output.flush()
                
                handleWebSocket(socket)
            } else {
                // Plain TCP - simple JSON line protocol
                handlePlainTcp(input, output)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client error", e)
        } finally {
            socket.close()
        }
    }
    
    private fun generateAcceptKey(key: String): String {
        val magic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        val sha1 = MessageDigest.getInstance("SHA-1")
        val hash = sha1.digest((key + magic).toByteArray())
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
    
    private suspend fun handleWebSocket(socket: Socket) = withContext(Dispatchers.IO) {
        val input = socket.getInputStream()
        val output = socket.getOutputStream()
        
        while (isRunning && socket.isConnected) {
            try {
                val frame = readWebSocketFrame(input) ?: break
                val response = processMessage(frame)
                if (response != null) {
                    writeWebSocketFrame(output, response)
                }
            } catch (e: Exception) {
                Log.e(TAG, "WebSocket error", e)
                break
            }
        }
    }
    
    private fun readWebSocketFrame(input: InputStream): String? {
        val b1 = input.read()
        if (b1 == -1) return null
        
        val b2 = input.read()
        val masked = (b2 and 0x80) != 0
        var len = b2 and 0x7F
        
        if (len == 126) {
            len = (input.read() shl 8) or input.read()
        } else if (len == 127) {
            // 8 bytes length - read but we only use last 4
            repeat(4) { input.read() }
            len = (input.read() shl 24) or (input.read() shl 16) or (input.read() shl 8) or input.read()
        }
        
        val mask = if (masked) ByteArray(4).also { input.read(it) } else null
        val data = ByteArray(len)
        var read = 0
        while (read < len) {
            read += input.read(data, read, len - read)
        }
        
        if (mask != null) {
            for (i in data.indices) {
                data[i] = (data[i].toInt() xor mask[i % 4].toInt()).toByte()
            }
        }
        
        return String(data)
    }
    
    private fun writeWebSocketFrame(output: OutputStream, text: String) {
        val data = text.toByteArray()
        output.write(0x81) // text frame, FIN
        
        when {
            data.size < 126 -> output.write(data.size)
            data.size < 65536 -> {
                output.write(126)
                output.write(data.size shr 8)
                output.write(data.size and 0xFF)
            }
            else -> {
                output.write(127)
                repeat(4) { output.write(0) }
                output.write(data.size shr 24)
                output.write((data.size shr 16) and 0xFF)
                output.write((data.size shr 8) and 0xFF)
                output.write(data.size and 0xFF)
            }
        }
        output.write(data)
        output.flush()
    }
    
    private suspend fun handlePlainTcp(input: BufferedReader, output: BufferedWriter) {
        while (isRunning) {
            val line = input.readLine() ?: break
            val response = processMessage(line)
            if (response != null) {
                output.write(response)
                output.newLine()
                output.flush()
            }
        }
    }
    
    private fun processMessage(json: String): String? {
        return try {
            val msg = JSONObject(json)
            when (msg.getString("type")) {
                "shell" -> handleShell(msg)
                "stream" -> handleStream(msg)
                "stop" -> handleStop(msg)
                "push" -> handlePush(msg)
                "pull" -> handlePull(msg)
                "ping" -> """{"type":"pong"}"""
                else -> """{"type":"error","message":"Unknown type"}"""
            }
        } catch (e: Exception) {
            """{"type":"error","message":"${e.message?.replace("\"", "\\\"")}"}"""
        }
    }
    
    private fun handleShell(msg: JSONObject): String {
        val cmd = msg.getString("cmd")
        val result = executeCommand(cmd)
        return JSONObject().apply {
            put("type", "output")
            put("stdout", result.stdout)
            put("stderr", result.stderr)
            put("exit", result.exitCode)
        }.toString()
    }
    
    private fun handleStream(msg: JSONObject): String {
        val cmd = msg.getString("cmd")
        val id = msg.optString("id", System.currentTimeMillis().toString())
        
        // Start streaming in background - responses sent separately
        // For now, just execute and return full output
        // TODO: implement true streaming with multiple responses
        val result = executeCommand(cmd)
        return JSONObject().apply {
            put("type", "output")
            put("id", id)
            put("stdout", result.stdout)
            put("stderr", result.stderr)
            put("exit", result.exitCode)
        }.toString()
    }
    
    private fun handleStop(msg: JSONObject): String {
        val id = msg.getString("id")
        streamingProcesses[id]?.destroy()
        streamingProcesses.remove(id)
        return """{"type":"ok"}"""
    }
    
    private fun handlePush(msg: JSONObject): String {
        val path = msg.getString("path")
        val data = Base64.decode(msg.getString("data"), Base64.DEFAULT)
        
        return try {
            // Use shell to write file (works with Shizuku permissions)
            val encoded = Base64.encodeToString(data, Base64.NO_WRAP)
            val result = executeCommand("echo '$encoded' | base64 -d > '$path'")
            if (result.exitCode == 0) {
                """{"type":"ok"}"""
            } else {
                """{"type":"error","message":"${result.stderr}"}"""
            }
        } catch (e: Exception) {
            """{"type":"error","message":"${e.message}"}"""
        }
    }
    
    private fun handlePull(msg: JSONObject): String {
        val path = msg.getString("path")
        
        return try {
            val result = executeCommand("base64 '$path'")
            if (result.exitCode == 0) {
                JSONObject().apply {
                    put("type", "file")
                    put("data", result.stdout.trim())
                }.toString()
            } else {
                """{"type":"error","message":"${result.stderr}"}"""
            }
        } catch (e: Exception) {
            """{"type":"error","message":"${e.message}"}"""
        }
    }
    
    private fun executeCommand(cmd: String): ShizukuManager.CommandResult {
        return if (useRoot) {
            // Use root
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                val stdout = process.inputStream.bufferedReader().readText()
                val stderr = process.errorStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                ShizukuManager.CommandResult(stdout, stderr, exitCode)
            } catch (e: Exception) {
                ShizukuManager.CommandResult("", "Root error: ${e.message}", -1)
            }
        } else {
            // Use Shizuku
            ShizukuManager.executeCommand(cmd)
        }
    }
}
