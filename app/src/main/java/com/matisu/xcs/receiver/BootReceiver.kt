package com.matisu.xcs.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.matisu.xcs.service.ControlService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            val serviceIntent = Intent(context, ControlService::class.java).apply {
                putExtra("auto_start", true)
            }
            context.startForegroundService(serviceIntent)
        }
    }
}
