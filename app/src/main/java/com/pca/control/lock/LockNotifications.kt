package com.pca.control.lock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pca.control.LockActivity
import android.R as AndroidR

object LockNotifications {
    const val CHANNEL_SERVICE = "pca_guard_service"
    const val CHANNEL_LOCK = "pca_lock_fullscreen"
    const val ID_SERVICE = 1001
    const val ID_LOCK = 1002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                "Guard listener",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps PCA listening for parental lock commands"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_LOCK,
                "Device lock",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows the lock screen over other apps"
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    fun serviceNotification(context: Context): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(AndroidR.drawable.ic_lock_lock)
            .setContentTitle("PCA Guard active")
            .setContentText("Listening for Parent lock commands")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun postFullScreenLock(context: Context) {
        ensureChannels(context)
        val launch = Intent(context, LockActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(LockActivity.EXTRA_START_LOCK_TASK, true)
        }
        val fullScreen = PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val content = PendingIntent.getActivity(
            context,
            1,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_LOCK)
            .setSmallIcon(AndroidR.drawable.ic_lock_lock)
            .setContentTitle("Device locked by Parent")
            .setContentText("Tap to enter unlock code")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(content)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.notify(ID_LOCK, notification)
    }

    fun cancelFullScreenLock(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.cancel(ID_LOCK)
    }
}
