package com.pca.control

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pca.control.commands.CommandExecutor
import com.pca.control.data.AppRole
import com.pca.control.devicepolicy.DevicePolicyController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return
        CommandExecutor.startListeningIfGuard(context.applicationContext)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? PcaApp ?: return@launch
                if (app.preferences.getRole() != AppRole.GUARD) return@launch
                if (!app.preferences.isLockActive()) return@launch
                val policy = DevicePolicyController(context.applicationContext, app.preferences)
                policy.launchLockUi(startLockTask = true)
            } finally {
                pending.finish()
            }
        }
    }
}
