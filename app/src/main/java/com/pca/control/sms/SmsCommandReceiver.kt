package com.pca.control.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.pca.control.PcaApp
import com.pca.control.commands.CommandExecutor
import com.pca.control.commands.RemoteCommand
import com.pca.control.data.AppRole
import com.pca.control.util.PhoneNumbers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val body = messages.joinToString(separator = "") { it.messageBody ?: "" }
        val originating = messages.firstOrNull()?.originatingAddress.orEmpty()
        val command = RemoteCommand.fromSmsBody(body) ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as PcaApp
                if (app.preferences.getRole() != AppRole.GUARD) return@launch
                val parentPhone = app.preferences.getParentPhone()
                if (parentPhone.isBlank() || !PhoneNumbers.matches(originating, parentPhone)) {
                    Log.w(TAG, "Ignoring SMS command from unmatched sender: $originating")
                    return@launch
                }
                CommandExecutor.execute(context, command, "sms")
            } catch (e: Exception) {
                Log.e(TAG, "SMS command failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SmsCommandReceiver"
    }
}
