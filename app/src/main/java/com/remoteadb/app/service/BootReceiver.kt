package com.remoteadb.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.remoteadb.app.utils.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val settingsRepository = SettingsRepository(context)
            
            CoroutineScope(Dispatchers.IO).launch {
                val autoStart = settingsRepository.autoStartOnBoot.first()
                
                if (autoStart) {
                    ADBService.startService(context)
                }
            }
        }
    }
}
