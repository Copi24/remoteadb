package com.remoteadb.app.shizuku

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import android.util.Log
import androidx.annotation.Keep
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * UserService that runs with shell (ADB) privileges via Shizuku.
 * This service can execute shell commands and perform file operations
 * with elevated privileges.
 */
class RemoteAdbUserService : IRemoteAdbService.Stub {
    
    companion object {
        private const val TAG = "RemoteAdbUserService"
    }
    
    /**
     * Default constructor required by Shizuku.
     */
    constructor() {
        Log.i(TAG, "Service created (default constructor)")
    }
    
    /**
     * Constructor with Context (available from Shizuku API v13).
     */
    @Keep
    constructor(context: Context) {
        Log.i(TAG, "Service created with context: ${context.packageName}")
    }
    
    override fun destroy() {
        Log.i(TAG, "Service destroyed")
        System.exit(0)
    }
    
    override fun exit() {
        Log.i(TAG, "Service exit requested")
        destroy()
    }
    
    override fun execCommand(command: String): String {
        return try {
            Log.d(TAG, "Executing command: $command")
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            process.waitFor()
            
            if (stderr.isNotEmpty()) {
                Log.w(TAG, "Command stderr: $stderr")
            }
            
            stdout
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed", e)
            "ERROR: ${e.message}"
        }
    }
    
    override fun execCommandWithExitCode(command: String): Int {
        return try {
            Log.d(TAG, "Executing command (with exit code): $command")
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            process.waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed", e)
            -1
        }
    }
    
    override fun readFile(path: String): ByteArray {
        return try {
            Log.d(TAG, "Reading file: $path")
            val file = File(path)
            if (!file.exists()) {
                Log.w(TAG, "File does not exist: $path")
                return ByteArray(0)
            }
            FileInputStream(file).use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read file: $path", e)
            ByteArray(0)
        }
    }
    
    override fun writeFile(path: String, data: ByteArray): Boolean {
        return try {
            Log.d(TAG, "Writing file: $path (${data.size} bytes)")
            val file = File(path)
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { it.write(data) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write file: $path", e)
            false
        }
    }
    
    override fun listDirectory(path: String): Array<String> {
        return try {
            Log.d(TAG, "Listing directory: $path")
            val dir = File(path)
            dir.list() ?: emptyArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list directory: $path", e)
            emptyArray()
        }
    }
    
    override fun exists(path: String): Boolean {
        return try {
            File(path).exists()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check existence: $path", e)
            false
        }
    }
    
    override fun delete(path: String): Boolean {
        return try {
            Log.d(TAG, "Deleting: $path")
            val file = File(path)
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete: $path", e)
            false
        }
    }
    
    override fun getPid(): Int {
        return Os.getpid()
    }
    
    override fun getUid(): Int {
        return Os.getuid()
    }
}
