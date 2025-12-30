package com.remoteadb.app.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class ManagedCfProvisionResponse(
    val hostname: String,
    val runToken: String
)

object ManagedCloudflareProvisioner {

    suspend fun provision(
        apiUrl: String,
        baseDomain: String,
        deviceId: String,
        adbPort: Int
    ): ManagedCfProvisionResponse =
        withContext(Dispatchers.IO) {
            val url = URL(apiUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 30000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val payload = "{\"deviceId\":\"$deviceId\",\"baseDomain\":\"$baseDomain\",\"adbPort\":$adbPort}"
            conn.outputStream.use { it.write(payload.toByteArray()) }

            val code = conn.responseCode
            val body = runCatching {
                (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()
                    ?.readText()
                    .orEmpty()
            }.getOrDefault("")

            if (code !in 200..299) {
                throw IllegalStateException("Provisioning failed ($code): ${body.take(200)}")
            }

            val hostname = "\"hostname\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(body)?.groupValues?.get(1)
                ?: throw IllegalStateException("Provisioning response missing hostname")
            val token = "\"token\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(body)?.groupValues?.get(1)
                ?: "\"runToken\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(body)?.groupValues?.get(1)
                ?: throw IllegalStateException("Provisioning response missing token")

            ManagedCfProvisionResponse(hostname = hostname, runToken = token)
        }
}
