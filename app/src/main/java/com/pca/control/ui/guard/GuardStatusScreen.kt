package com.pca.control.ui.guard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun GuardStatusScreen(
    isAdminActive: Boolean,
    isDeviceOwner: Boolean,
    lockActive: Boolean,
    launcherHidden: Boolean,
    onEnableAdmin: () -> Unit,
    onRequestSmsPermission: () -> Unit,
    onDisableBatteryOptimization: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Guard active",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                lockActive -> "Device is locked by Parent"
                else -> "Linked and listening for Parent commands (background service on)"
            },
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            buildString {
                append(if (isAdminActive) "Device Admin: on" else "Device Admin: off")
                append(" · ")
                append(if (isDeviceOwner) "Device Owner: on" else "Device Owner: off")
                if (launcherHidden) append(" · Icon hidden")
            },
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (isDeviceOwner) {
                "Device Owner kiosk: Home / Recents are blocked while locked (no pin prompt)."
            } else {
                "Device Owner is OFF. Without it, Android cannot force Recents/Home lock — the child can leave. Set Device Owner via ADB (see README) for real lock."
            },
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = if (isDeviceOwner) {
                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.error
            }
        )
        Spacer(Modifier.height(24.dp))
        if (!isAdminActive) {
            Button(onClick = onEnableAdmin, modifier = Modifier.fillMaxWidth()) {
                Text("Enable device admin")
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick = onRequestSmsPermission, modifier = Modifier.fillMaxWidth()) {
            Text("Grant SMS / notification permissions")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onDisableBatteryOptimization, modifier = Modifier.fillMaxWidth()) {
            Text("Allow unrestricted battery use")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "SMS commands are accepted only from the Parent phone number saved during pairing. Keep battery unrestricted so lock works when PCA is in the background.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}
