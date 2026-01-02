package com.remoteadb.app

import android.app.Application

class RemoteADBApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Persist crash info so users can report it without logcat.
        val prefs = getSharedPreferences("remote_adb_crash", MODE_PRIVATE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val text = buildString {
                    appendLine(e.toString())
                    e.stackTrace.take(60).forEach { appendLine("  at $it") }
                    e.cause?.let { c ->
                        appendLine("Caused by: ${c}")
                        c.stackTrace.take(30).forEach { appendLine("  at $it") }
                    }
                }
                prefs.edit().putString("last_crash", text).apply()
            }
            previous?.uncaughtException(t, e)
        }
    }
}
