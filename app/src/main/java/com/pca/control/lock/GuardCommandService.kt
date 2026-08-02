package com.pca.control.lock

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.pca.control.LockActivity
import com.pca.control.PcaApp
import com.pca.control.commands.CommandExecutor
import com.pca.control.commands.RemoteCommand
import com.pca.control.data.AppRole
import com.pca.control.data.LinkStatus
import com.pca.control.devicepolicy.DevicePolicyController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Keeps Guard listening for parental commands while linked, and re-surfaces
 * LockActivity when the OS blocks background activity starts.
 */
class GuardCommandService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var registration: ListenerRegistration? = null
    private var watchdogJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        LockNotifications.ensureChannels(this)
        startAsForeground()
        startCommandListener()
        startWatchdog()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (registration == null) startCommandListener()
        if (watchdogJob?.isActive != true) startWatchdog()
        return START_STICKY
    }

    override fun onDestroy() {
        registration?.remove()
        registration = null
        watchdogJob?.cancel()
        watchdogJob = null
        super.onDestroy()
    }

    private fun startAsForeground() {
        val notification = LockNotifications.serviceNotification(this)
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                LockNotifications.ID_SERVICE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(LockNotifications.ID_SERVICE, notification)
        }
    }

    private fun startCommandListener() {
        scope.launch {
            val app = applicationContext as? PcaApp ?: return@launch
            val prefs = app.preferences
            if (prefs.getRole() != AppRole.GUARD) {
                stopSelf()
                return@launch
            }
            if (prefs.getLinkStatus() != LinkStatus.LINKED) {
                stopSelf()
                return@launch
            }
            val deviceId = prefs.getDeviceId()
            if (deviceId.isBlank()) return@launch

            registration?.remove()
            registration = FirebaseFirestore.getInstance()
                .collection("devices").document(deviceId)
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
                            CommandExecutor.execute(applicationContext, command, "firestore-fgs")
                            runCatching {
                                doc.reference.update("status", "done").await()
                            }
                        }
                    }
                }
            Log.i(TAG, "Guard command listener attached")
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            val app = applicationContext as? PcaApp ?: return@launch
            val policy = DevicePolicyController(applicationContext, app.preferences)
            while (isActive) {
                delay(WATCHDOG_MS)
                try {
                    if (app.preferences.getRole() != AppRole.GUARD) continue
                    if (!app.preferences.isLockActive()) {
                        LockNotifications.cancelFullScreenLock(applicationContext)
                        continue
                    }
                    if (!LockActivity.isResumedNow) {
                        Log.i(TAG, "Watchdog: re-showing lock UI")
                        policy.launchLockUiImmediate(startLockTask = true)
                        // Notification only as silent backup if still not visible shortly after
                        delay(300)
                        if (!LockActivity.isResumedNow) {
                            LockNotifications.postFullScreenLock(applicationContext)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Watchdog tick failed", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "GuardCommandService"
        private const val WATCHDOG_MS = 500L

        fun start(context: Context) {
            val app = context.applicationContext
            val intent = Intent(app, GuardCommandService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(
                Intent(context.applicationContext, GuardCommandService::class.java)
            )
        }
    }
}
