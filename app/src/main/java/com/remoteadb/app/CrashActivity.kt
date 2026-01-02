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

        if (crash.isBlank()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_crash)

        findViewById<TextView>(R.id.body).text = crash
        findViewById<Button>(R.id.clear).setOnClickListener {
            prefs.edit().remove("last_crash").apply()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}

