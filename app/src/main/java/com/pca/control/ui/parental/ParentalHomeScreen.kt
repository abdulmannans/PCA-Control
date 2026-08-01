package com.pca.control.ui.parental

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pca.control.commands.RemoteCommand

@Composable
fun ParentalHomeScreen(
    guardPhone: String,
    statusMessage: String?,
    sending: Boolean,
    onSavePhone: (String) -> Unit,
    onSend: (RemoteCommand) -> Unit
) {
    var phone by remember(guardPhone) { mutableStateOf(guardPhone) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Parental controls",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Commands are sent over Firebase and SMS (when a Guard number is set).",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it.filter { ch -> ch.isDigit() || ch == '+' }.take(16) },
            label = { Text("Guard phone number") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )
        OutlinedButton(
            onClick = { onSavePhone(phone) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save phone number")
        }

        Spacer(Modifier.height(8.dp))
        Text("Actions", fontWeight = FontWeight.SemiBold)

        Button(
            onClick = { onSend(RemoteCommand.LOCK) },
            enabled = !sending,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Lock device (recommended)")
        }
        Button(
            onClick = { onSend(RemoteCommand.LOCK_BLOCK) },
            enabled = !sending,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Lock + block apps")
        }
        OutlinedButton(
            onClick = { onSend(RemoteCommand.UNLOCK) },
            enabled = !sending,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Unlock / clear app block")
        }

        OutlinedButton(
            onClick = { },
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Power off (not workable / risky)")
        }
        Text(
            "Android does not allow third-party apps to power off the phone without root or system privileges. This control is intentionally disabled.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )

        if (statusMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(statusMessage, color = MaterialTheme.colorScheme.secondary)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "SMS keywords (if you text the Guard phone): PCA LOCK · PCA LOCKBLOCK · PCA UNLOCK",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}
