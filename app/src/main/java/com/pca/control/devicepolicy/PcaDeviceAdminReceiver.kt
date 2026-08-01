package com.pca.control.devicepolicy

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class PcaDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "PCA device admin enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "PCA device admin disabled", Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun component(context: Context) =
            android.content.ComponentName(context, PcaDeviceAdminReceiver::class.java)
    }
}
