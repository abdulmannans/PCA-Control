package com.pca.control.devicepolicy

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.pca.control.LockActivity
import com.pca.control.commands.RemoteCommand
import com.pca.control.data.AppPreferences
import com.pca.control.lock.LockNotifications
import com.pca.control.util.PhoneNumbers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class DevicePolicyController(
    private val context: Context,
    private val preferences: AppPreferences
) {
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = PcaDeviceAdminReceiver.component(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val db = FirebaseFirestore.getInstance()

    fun isAdminActive(): Boolean = dpm.isAdminActive(admin)

    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(context.packageName)

    fun requestAdminIntent(): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Required to lock the device remotely."
            )
        }

    fun execute(command: RemoteCommand) {
        when (command) {
            RemoteCommand.LOCK -> activateLock()
            RemoteCommand.UNLOCK -> clearLock()
        }
    }

    fun activateLock() {
        scope.launch {
            try {
                val pin = generatePin()
                preferences.setLockPin(pin)
                preferences.setLockActive(true)
                deliverPinToParent(pin)
                launchLockUi(startLockTask = true)
                LockNotifications.postFullScreenLock(context)
            } catch (e: Exception) {
                Log.e(TAG, "activateLock failed", e)
            }
        }
    }

    fun clearLock() {
        scope.launch {
            try {
                preferences.clearLock()
                clearParentLockFields()
                disableLockHomeAlias()
                LockNotifications.cancelFullScreenLock(context)
                if (isDeviceOwner()) {
                    runCatching {
                        dpm.setLockTaskPackages(admin, emptyArray())
                    }
                }
                context.sendBroadcast(
                    Intent(ACTION_LOCK_CLEARED).setPackage(context.packageName)
                )
            } catch (e: Exception) {
                Log.e(TAG, "clearLock failed", e)
            }
        }
    }

    suspend fun unlockWithPin(entered: String): Boolean {
        val expected = preferences.getLockPin()
        if (expected.isBlank() || entered != expected) return false
        preferences.clearLock()
        clearParentLockFields()
        disableLockHomeAlias()
        LockNotifications.cancelFullScreenLock(context)
        if (isDeviceOwner()) {
            runCatching { dpm.setLockTaskPackages(admin, emptyArray()) }
        }
        context.sendBroadcast(
            Intent(ACTION_LOCK_CLEARED).setPackage(context.packageName)
        )
        return true
    }

    fun launchLockUi(startLockTask: Boolean = false) {
        enableLockHomeAlias()
        val intent = Intent(context, LockActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            // Always request lock-task / screen-pinning attempt
            putExtra(LockActivity.EXTRA_START_LOCK_TASK, startLockTask)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startActivity(LockActivity) failed — relying on full-screen notification", e)
            LockNotifications.postFullScreenLock(context)
        }
    }

    /** Device Owner = hard kiosk; otherwise screen pinning (may prompt once). */
    fun enableLockTask(activity: android.app.Activity) {
        try {
            if (isDeviceOwner()) {
                dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
            }
            activity.startLockTask()
        } catch (e: Exception) {
            Log.w(TAG, "startLockTask / screen pin failed", e)
        }
    }

    fun stopLockTaskIfNeeded(activity: android.app.Activity) {
        try {
            if (activity.isInLockTaskModeCompat()) {
                activity.stopLockTask()
            }
        } catch (e: Exception) {
            Log.w(TAG, "stopLockTask failed", e)
        }
    }

    private fun android.app.Activity.isInLockTaskModeCompat(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return am.lockTaskModeState != android.app.ActivityManager.LOCK_TASK_MODE_NONE
    }

    private suspend fun deliverPinToParent(pin: String) {
        val parentPhone = preferences.getParentPhone()
        if (parentPhone.isNotBlank() && PhoneNumbers.isValid(parentPhone)) {
            try {
                val sms = context.getSystemService(SmsManager::class.java)
                    ?: SmsManager.getDefault()
                sms.sendTextMessage(parentPhone, null, "PCA UNLOCK CODE $pin", null, null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to SMS unlock PIN", e)
            }
        }

        val parentId = preferences.getPeerDeviceId()
        if (parentId.isNotBlank()) {
            runCatching {
                db.collection("devices").document(parentId).update(
                    mapOf(
                        "lockActive" to true,
                        "activeLockPin" to pin,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }.onFailure { Log.e(TAG, "Failed to write PIN to parent device doc", it) }
        }

        val guardId = preferences.getDeviceId()
        if (guardId.isNotBlank()) {
            runCatching {
                db.collection("devices").document(guardId).update(
                    mapOf(
                        "lockActive" to true,
                        "activeLockPin" to pin,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
    }

    private suspend fun clearParentLockFields() {
        val parentId = preferences.getPeerDeviceId()
        if (parentId.isNotBlank()) {
            runCatching {
                db.collection("devices").document(parentId).update(
                    mapOf(
                        "lockActive" to false,
                        "activeLockPin" to "",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
        val guardId = preferences.getDeviceId()
        if (guardId.isNotBlank()) {
            runCatching {
                db.collection("devices").document(guardId).update(
                    mapOf(
                        "lockActive" to false,
                        "activeLockPin" to "",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
    }

    private fun enableLockHomeAlias() {
        val alias = ComponentName(context, "com.pca.control.LockHomeAlias")
        context.packageManager.setComponentEnabledSetting(
            alias,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun disableLockHomeAlias() {
        val alias = ComponentName(context, "com.pca.control.LockHomeAlias")
        context.packageManager.setComponentEnabledSetting(
            alias,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    fun hideLauncherIcon() {
        val alias = ComponentName(context, "com.pca.control.LauncherAlias")
        context.packageManager.setComponentEnabledSetting(
            alias,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        scope.launch { preferences.setLauncherHidden(true) }
    }

    fun showLauncherIcon() {
        val alias = ComponentName(context, "com.pca.control.LauncherAlias")
        context.packageManager.setComponentEnabledSetting(
            alias,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        scope.launch { preferences.setLauncherHidden(false) }
    }

    private fun generatePin(): String =
        (1..6).map { Random.nextInt(0, 10) }.joinToString("")

    companion object {
        private const val TAG = "DevicePolicyController"
        const val ACTION_LOCK_CLEARED = "com.pca.control.LOCK_CLEARED"
    }
}
