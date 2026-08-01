package com.pca.control.commands

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pca.control.PcaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PcaFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val app = application as PcaApp
        CoroutineScope(Dispatchers.IO).launch {
            app.preferences.setFcmToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val action = message.data["action"] ?: message.data["command"]
        val command = RemoteCommand.fromWire(action)
        if (command == null) {
            Log.w(TAG, "Unknown FCM action: $action")
            return
        }
        CommandExecutor.execute(this, command, "fcm")
    }

    companion object {
        private const val TAG = "PcaFcmService"
    }
}
