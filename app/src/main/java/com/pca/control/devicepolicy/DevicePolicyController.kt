package com.pca.control.devicepolicy

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.pca.control.commands.RemoteCommand
import com.pca.control.data.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DevicePolicyController(
    private val context: Context,
    private val preferences: AppPreferences
) {
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = PcaDeviceAdminReceiver.component(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    fun isAdminActive(): Boolean = dpm.isAdminActive(admin)

    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(context.packageName)

    fun requestAdminIntent(): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Required to lock the device and block apps remotely."
            )
        }

    fun execute(command: RemoteCommand) {
        when (command) {
            RemoteCommand.LOCK -> lockNow()
            RemoteCommand.LOCK_BLOCK -> lockAndBlock()
            RemoteCommand.UNLOCK -> unlockBlock()
        }
    }

    fun lockNow(): Boolean {
        return try {
            if (!isAdminActive()) {
                Log.w(TAG, "Admin not active; cannot lock")
                return false
            }
            dpm.lockNow()
            true
        } catch (e: Exception) {
            Log.e(TAG, "lockNow failed", e)
            false
        }
    }

    fun lockAndBlock(): Boolean {
        val locked = lockNow()
        if (!isDeviceOwner()) {
            Log.w(TAG, "Not device owner; lock only (no package suspend)")
            scope.launch { preferences.setBlockActive(false) }
            return locked
        }
        return try {
            val toSuspend = packagesToSuspend()
            if (toSuspend.isNotEmpty()) {
                dpm.setPackagesSuspended(admin, toSuspend, true)
            }
            scope.launch { preferences.setBlockActive(true) }
            locked
        } catch (e: Exception) {
            Log.e(TAG, "lockAndBlock failed", e)
            false
        }
    }

    fun unlockBlock(): Boolean {
        return try {
            if (isDeviceOwner()) {
                val toResume = packagesToSuspend()
                if (toResume.isNotEmpty()) {
                    dpm.setPackagesSuspended(admin, toResume, false)
                }
            }
            scope.launch { preferences.setBlockActive(false) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "unlockBlock failed", e)
            false
        }
    }

    fun hideLauncherIcon() {
        val alias = ComponentName(context, "com.pca.control.LauncherAlias")
        context.packageManager.setComponentEnabledSetting(
            alias,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        // Also remove MAIN/LAUNCHER from being discoverable if MainActivity somehow listed
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

    private fun packagesToSuspend(): Array<String> {
        val pm = context.packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val skip = setOf(
            context.packageName,
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",
            "com.android.dialer",
            "com.android.server.telecom",
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.vending",
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.huawei.android.launcher"
        )
        return installed
            .asSequence()
            .filter { app ->
                (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                    app.packageName !in skip &&
                    pm.getLaunchIntentForPackage(app.packageName) != null
            }
            .map { it.packageName }
            .distinct()
            .toList()
            .toTypedArray()
    }

    companion object {
        private const val TAG = "DevicePolicyController"
    }
}
