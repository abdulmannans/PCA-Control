package com.pca.control.pairing

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import com.pca.control.data.AppPreferences
import com.pca.control.data.LinkStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class PairingRepository(
    private val preferences: AppPreferences,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun refreshFcmToken(): String {
        val token = FirebaseMessaging.getInstance().token.await()
        preferences.setFcmToken(token)
        return token
    }

    suspend fun startGuardPairing(): String {
        val deviceId = preferences.ensureDeviceId()
        val token = runCatching { refreshFcmToken() }.getOrDefault("")
        val code = generateCode()
        val payload = hashMapOf(
            "code" to code,
            "guardDeviceId" to deviceId,
            "guardFcmToken" to token,
            "status" to "waiting",
            "createdAt" to FieldValue.serverTimestamp(),
            "expiresAtMs" to System.currentTimeMillis() + PAIR_TTL_MS
        )
        db.collection(COL_PAIRINGS).document(code).set(payload).await()
        preferences.setPairCode(code)
        preferences.setPairId(code)
        preferences.setLinkStatus(LinkStatus.WAITING)
        return code
    }

    fun observeGuardPairing(code: String): Flow<PairingState> = callbackFlow {
        val reg: ListenerRegistration = db.collection(COL_PAIRINGS).document(code)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    trySend(PairingState.Error(error.message ?: "Pairing listener failed"))
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) {
                    trySend(PairingState.Waiting)
                    return@addSnapshotListener
                }
                val status = snap.getString("status") ?: "waiting"
                if (status == "linked") {
                    val parentId = snap.getString("parentDeviceId").orEmpty()
                    trySend(PairingState.Linked(peerDeviceId = parentId, pairId = code))
                } else {
                    trySend(PairingState.Waiting)
                }
            }
        awaitClose { reg.remove() }
    }

    suspend fun linkAsParent(code: String, guardPhone: String = ""): Result<Unit> = runCatching {
        val normalized = code.trim().uppercase()
        require(normalized.length == 6) { "Enter the 6-character code from the Guard phone" }
        val deviceId = preferences.ensureDeviceId()
        val token = runCatching { refreshFcmToken() }.getOrDefault("")
        val ref = db.collection(COL_PAIRINGS).document(normalized)
        val snap = ref.get().await()
        require(snap.exists()) { "Code not found or expired" }
        val status = snap.getString("status")
        require(status == "waiting") { "Code already used or not waiting" }
        val expires = snap.getLong("expiresAtMs") ?: 0L
        require(System.currentTimeMillis() <= expires) { "Code expired — generate a new one on Guard" }
        val guardDeviceId = snap.getString("guardDeviceId").orEmpty()
        require(guardDeviceId.isNotBlank()) { "Invalid pairing document" }

        ref.update(
            mapOf(
                "status" to "linked",
                "parentDeviceId" to deviceId,
                "parentFcmToken" to token,
                "guardPhone" to guardPhone,
                "linkedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        db.collection(COL_DEVICES).document(guardDeviceId).set(
            mapOf(
                "role" to "guard",
                "pairedWith" to deviceId,
                "pairId" to normalized,
                "fcmToken" to (snap.getString("guardFcmToken") ?: ""),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        db.collection(COL_DEVICES).document(deviceId).set(
            mapOf(
                "role" to "parental",
                "pairedWith" to guardDeviceId,
                "pairId" to normalized,
                "fcmToken" to token,
                "guardPhone" to guardPhone,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        preferences.setPairCode(normalized)
        preferences.setPairId(normalized)
        preferences.setPeerDeviceId(guardDeviceId)
        if (guardPhone.isNotBlank()) preferences.setGuardPhone(guardPhone)
        preferences.setLinkStatus(LinkStatus.LINKED)
    }

    suspend fun markGuardLinked(peerDeviceId: String, pairId: String) {
        preferences.setPeerDeviceId(peerDeviceId)
        preferences.setPairId(pairId)
        preferences.setLinkStatus(LinkStatus.LINKED)
        val deviceId = preferences.ensureDeviceId()
        val token = preferences.getFcmToken()
        db.collection(COL_DEVICES).document(deviceId).set(
            mapOf(
                "role" to "guard",
                "pairedWith" to peerDeviceId,
                "pairId" to pairId,
                "fcmToken" to token,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    private fun generateCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
    }

    companion object {
        const val COL_PAIRINGS = "pairings"
        const val COL_DEVICES = "devices"
        const val PAIR_TTL_MS = 10 * 60 * 1000L
    }
}

sealed class PairingState {
    data object Waiting : PairingState()
    data class Linked(val peerDeviceId: String, val pairId: String) : PairingState()
    data class Error(val message: String) : PairingState()
}
