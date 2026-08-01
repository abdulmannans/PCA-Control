package com.pca.control

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pca.control.commands.CommandExecutor

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return
        CommandExecutor.startListeningIfGuard(context.applicationContext)
    }
}
