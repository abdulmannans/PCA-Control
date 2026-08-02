package com.pca.control.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pca_prefs")

class AppPreferences(private val context: Context) {

    private object Keys {
        val ROLE = stringPreferencesKey("role")
        val LINK_STATUS = stringPreferencesKey("link_status")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val PAIR_CODE = stringPreferencesKey("pair_code")
        val PAIR_ID = stringPreferencesKey("pair_id")
        val PEER_DEVICE_ID = stringPreferencesKey("peer_device_id")
        val GUARD_PHONE = stringPreferencesKey("guard_phone")
        val PARENT_PHONE = stringPreferencesKey("parent_phone")
        val FCM_TOKEN = stringPreferencesKey("fcm_token")
        val LAUNCHER_HIDDEN = booleanPreferencesKey("launcher_hidden")
        val BLOCK_ACTIVE = booleanPreferencesKey("block_active")
        val LOCK_ACTIVE = booleanPreferencesKey("lock_active")
        val LOCK_PIN = stringPreferencesKey("lock_pin")
    }

    val roleFlow: Flow<AppRole> = context.dataStore.data.map { prefs ->
        runCatching { AppRole.valueOf(prefs[Keys.ROLE] ?: AppRole.NONE.name) }
            .getOrDefault(AppRole.NONE)
    }

    val linkStatusFlow: Flow<LinkStatus> = context.dataStore.data.map { prefs ->
        runCatching { LinkStatus.valueOf(prefs[Keys.LINK_STATUS] ?: LinkStatus.UNLINKED.name) }
            .getOrDefault(LinkStatus.UNLINKED)
    }

    val guardPhoneFlow: Flow<String> = context.dataStore.data.map { it[Keys.GUARD_PHONE] ?: "" }
    val parentPhoneFlow: Flow<String> = context.dataStore.data.map { it[Keys.PARENT_PHONE] ?: "" }
    val pairCodeFlow: Flow<String> = context.dataStore.data.map { it[Keys.PAIR_CODE] ?: "" }
    val peerDeviceIdFlow: Flow<String> = context.dataStore.data.map { it[Keys.PEER_DEVICE_ID] ?: "" }
    val deviceIdFlow: Flow<String> = context.dataStore.data.map { it[Keys.DEVICE_ID] ?: "" }
    val blockActiveFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.BLOCK_ACTIVE] ?: false }
    val lockActiveFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.LOCK_ACTIVE] ?: false }
    val lockPinFlow: Flow<String> = context.dataStore.data.map { it[Keys.LOCK_PIN] ?: "" }

    suspend fun getRole(): AppRole = roleFlow.first()
    suspend fun getLinkStatus(): LinkStatus = linkStatusFlow.first()
    suspend fun getDeviceId(): String = ensureDeviceId()
    suspend fun getPairCode(): String = pairCodeFlow.first()
    suspend fun getPeerDeviceId(): String = peerDeviceIdFlow.first()
    suspend fun getGuardPhone(): String = guardPhoneFlow.first()
    suspend fun getParentPhone(): String = parentPhoneFlow.first()
    suspend fun getFcmToken(): String = context.dataStore.data.first()[Keys.FCM_TOKEN] ?: ""
    suspend fun isLauncherHidden(): Boolean =
        context.dataStore.data.first()[Keys.LAUNCHER_HIDDEN] ?: false
    suspend fun isBlockActive(): Boolean = blockActiveFlow.first()
    suspend fun isLockActive(): Boolean = lockActiveFlow.first()
    suspend fun getLockPin(): String = lockPinFlow.first()

    suspend fun setRole(role: AppRole) {
        context.dataStore.edit { it[Keys.ROLE] = role.name }
    }

    suspend fun setLinkStatus(status: LinkStatus) {
        context.dataStore.edit { it[Keys.LINK_STATUS] = status.name }
    }

    suspend fun setPairCode(code: String) {
        context.dataStore.edit { it[Keys.PAIR_CODE] = code }
    }

    suspend fun setPairId(id: String) {
        context.dataStore.edit { it[Keys.PAIR_ID] = id }
    }

    suspend fun setPeerDeviceId(id: String) {
        context.dataStore.edit { it[Keys.PEER_DEVICE_ID] = id }
    }

    suspend fun setGuardPhone(phone: String) {
        context.dataStore.edit { it[Keys.GUARD_PHONE] = phone }
    }

    suspend fun setParentPhone(phone: String) {
        context.dataStore.edit { it[Keys.PARENT_PHONE] = phone }
    }

    suspend fun setFcmToken(token: String) {
        context.dataStore.edit { it[Keys.FCM_TOKEN] = token }
    }

    suspend fun setLauncherHidden(hidden: Boolean) {
        context.dataStore.edit { it[Keys.LAUNCHER_HIDDEN] = hidden }
    }

    suspend fun setBlockActive(active: Boolean) {
        context.dataStore.edit { it[Keys.BLOCK_ACTIVE] = active }
    }

    suspend fun setLockActive(active: Boolean) {
        context.dataStore.edit { it[Keys.LOCK_ACTIVE] = active }
    }

    suspend fun setLockPin(pin: String) {
        context.dataStore.edit { it[Keys.LOCK_PIN] = pin }
    }

    suspend fun clearLock() {
        context.dataStore.edit {
            it[Keys.LOCK_ACTIVE] = false
            it[Keys.LOCK_PIN] = ""
        }
    }

    suspend fun ensureDeviceId(): String {
        val existing = context.dataStore.data.first()[Keys.DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing
        val id = java.util.UUID.randomUUID().toString()
        context.dataStore.edit { it[Keys.DEVICE_ID] = id }
        return id
    }
}
