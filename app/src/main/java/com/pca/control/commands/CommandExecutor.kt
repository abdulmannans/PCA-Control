package com.pca.control.commands

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.pca.control.PcaApp
import com.pca.control.data.AppRole
import com.pca.control.data.LinkStatus
import com.pca.control.devicepolicy.DevicePolicyController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object CommandExecutor {
    private const val TAG = "CommandExecutor"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var registration: ListenerRegistration? = null

    fun execute(context: Context, command: RemoteCommand, source: String) {
        Log.i(TAG, "Executing ${command.wire} from $source")
        val app = context.applicationContext as PcaApp
        val controller = DevicePolicyController(context.applicationContext, app.preferences)
        controller.execute(command)
    }

    fun startListeningIfGuard(context: Context) {
        scope.launch {
            val app = context.applicationContext as PcaApp
            val prefs = app.preferences
            if (prefs.getRole() != AppRole.GUARD) return@launch
            if (prefs.getLinkStatus() != LinkStatus.LINKED) return@launch
            val deviceId = prefs.getDeviceId()
            if (deviceId.isBlank()) return@launch

            registration?.remove()
            val db = FirebaseFirestore.getInstance()
            registration = db.collection("devices").document(deviceId)
                .collection("commands")
                .whereEqualTo("status", "pending")
                .addSnapshotListener { snap, error ->
                    if (error != null) {
                        Log.e(TAG, "Command listener error", error)
                        return@addSnapshotListener
                    }
                    snap?.documentChanges?.forEach { change ->
                        val doc = change.document
                        val action = doc.getString("action")
                        val command = RemoteCommand.fromWire(action) ?: return@forEach
                        scope.launch {
                            execute(context, command, "firestore")
                            runCatching {
                                doc.reference.update("status", "done").await()
                            }
                        }
                    }
                }
        }
    }

    fun stopListening() {
        registration?.remove()
        registration = null
    }
}
