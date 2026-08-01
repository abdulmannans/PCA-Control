package com.pca.control.commands

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.pca.control.data.AppPreferences
import kotlinx.coroutines.tasks.await

class CommandSender(
    private val context: Context,
    private val preferences: AppPreferences,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun send(command: RemoteCommand): Result<Unit> = runCatching {
        val guardId = preferences.getPeerDeviceId()
        require(guardId.isNotBlank()) { "Not linked to a Guard device" }

        db.collection("devices").document(guardId)
            .collection("commands")
            .add(
                mapOf(
                    "action" to command.wire,
                    "status" to "pending",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "source" to "firebase"
                )
            ).await()

        val phone = preferences.getGuardPhone()
        if (phone.isNotBlank()) {
            sendSms(phone, command.smsKeyword)
        }
    }

    private fun sendSms(phone: String, body: String) {
        try {
            val sms = context.getSystemService(SmsManager::class.java)
                ?: SmsManager.getDefault()
            sms.sendTextMessage(phone, null, body, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "SMS send failed (Firebase command still queued)", e)
        }
    }

    companion object {
        private const val TAG = "CommandSender"
    }
}
