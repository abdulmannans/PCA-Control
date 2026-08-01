package com.pca.control.ui.guard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
    blockActive: Boolean,
    launcherHidden: Boolean,
    onEnableAdmin: () -> Unit,
    onRequestSmsPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
            "This phone is linked. The app icon is ${if (launcherHidden) "hidden" else "visible"} in the app drawer. Open via Settings → Apps → PCA Control if needed.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            buildString {
                append(if (isAdminActive) "Device Admin: ON\n" else "Device Admin: OFF\n")
                append(
                    if (isDeviceOwner) "Device Owner: ON (uninstall protected)\n"
                    else "Device Owner: OFF — run ADB set-device-owner for uninstall protection\n"
                )
                append(if (blockActive) "App block: ACTIVE" else "App block: inactive")
            },
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        if (!isAdminActive) {
            Button(onClick = onEnableAdmin, modifier = Modifier.fillMaxWidth()) {
                Text("Enable device admin")
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick = onRequestSmsPermission, modifier = Modifier.fillMaxWidth()) {
            Text("Allow SMS commands")
        }
    }
}
