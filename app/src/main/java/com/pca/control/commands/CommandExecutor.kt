package com.pca.control.commands

import android.content.Context
import android.util.Log
import com.pca.control.PcaApp
import com.pca.control.data.AppRole
import com.pca.control.data.LinkStatus
import com.pca.control.devicepolicy.DevicePolicyController
import com.pca.control.lock.GuardCommandService

object CommandExecutor {
    private const val TAG = "CommandExecutor"

    fun execute(context: Context, command: RemoteCommand, source: String) {
        Log.i(TAG, "Executing ${command.wire} from $source")
        val app = context.applicationContext as PcaApp
        val controller = DevicePolicyController(context.applicationContext, app.preferences)
        controller.execute(command)
    }

    /** Starts the Guard foreground service (preferred) which owns the Firestore listener. */
    fun startListeningIfGuard(context: Context) {
        val app = context.applicationContext as? PcaApp ?: return
        // Fire-and-forget check via service start; service self-stops if not Guard/linked
        try {
            // Quick sync peek via blocking prefs is avoided — service checks async
            GuardCommandService.start(context.applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start GuardCommandService", e)
        }
    }

    suspend fun startListeningIfGuardSuspend(context: Context) {
        val app = context.applicationContext as PcaApp
        val prefs = app.preferences
        if (prefs.getRole() != AppRole.GUARD) return
        if (prefs.getLinkStatus() != LinkStatus.LINKED) return
        GuardCommandService.start(context.applicationContext)
    }

    fun stopListening(context: Context) {
        GuardCommandService.stop(context)
    }
}
