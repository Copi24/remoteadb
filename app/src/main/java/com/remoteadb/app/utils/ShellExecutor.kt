package com.remoteadb.app.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object ShellExecutor {
    
    suspend fun executeAsRoot(command: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = StringBuilder()
            val error = StringBuilder()
            
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.appendLine(line)
                }
            }
            
            BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    error.appendLine(line)
                }
            }
            
            val exitCode = process.waitFor()
            ShellResult(
                success = exitCode == 0,
                output = output.toString().trim(),
                error = error.toString().trim(),
                exitCode = exitCode
            )
        } catch (e: Exception) {
            ShellResult(
                success = false,
                output = "",
                error = e.message ?: "Unknown error",
                exitCode = -1
            )
        }
    }
    
    suspend fun execute(command: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val output = StringBuilder()
            val error = StringBuilder()
            
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.appendLine(line)
                }
            }
            
            BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    error.appendLine(line)
                }
            }
            
            val exitCode = process.waitFor()
            ShellResult(
                success = exitCode == 0,
                output = output.toString().trim(),
                error = error.toString().trim(),
                exitCode = exitCode
            )
        } catch (e: Exception) {
            ShellResult(
                success = false,
                output = "",
                error = e.message ?: "Unknown error",
                exitCode = -1
            )
        }
    }
    
    suspend fun checkRootAccess(): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = executeAsRoot("id")
            result.success && result.output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }
    
}

data class ShellResult(
    val success: Boolean,
    val output: String,
    val error: String,
    val exitCode: Int
)
