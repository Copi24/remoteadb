package com.remoteadb.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class CrashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("remote_adb_crash", MODE_PRIVATE)
        val crash = prefs.getString("last_crash", "").orEmpty()

        setContentView(R.layout.activity_crash)

        findViewById<TextView>(R.id.body).text = if (crash.isBlank()) {
            "No Java crash recorded yet.\n\nIf the app still closes instantly, it's likely a native (SIG*) crash or a process kill before the Java handler runs.\n\nTap 'Open App' to try launching MainActivity."
        } else {
            crash
        }

        findViewById<Button>(R.id.open).setOnClickListener {
            prefs.edit().putBoolean("starting_main", true).apply()
            startActivity(Intent(this@CrashActivity, MainActivity::class.java))
        }

        findViewById<Button>(R.id.clear).setOnClickListener {
            prefs.edit().remove("last_crash").remove("starting_main").apply()
            findViewById<TextView>(R.id.body).text = "Cleared crash info. Tap 'Open App' to try again."
        }
    }
}

