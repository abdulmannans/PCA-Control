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
import androidx.compose.ui.unit.sp
import com.pca.control.commands.RemoteCommand
import com.pca.control.util.PhoneNumbers

@Composable
fun ParentalHomeScreen(
    guardPhone: String,
    activeLockPin: String?,
    lockActive: Boolean,
    statusMessage: String?,
    sending: Boolean,
    onSavePhone: (String) -> Unit,
    onSend: (RemoteCommand) -> Unit
) {
    var phone by remember(guardPhone) { mutableStateOf(guardPhone) }
    var phoneError by remember { mutableStateOf<String?>(null) }

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
            onValueChange = {
                phone = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' || ch == '-' }.take(20)
                phoneError = null
            },
            label = { Text("Guard phone number") },
            placeholder = { Text("+91…") },
            supportingText = { Text("For SMS commands — include country code when possible") },
            singleLine = true,
            isError = phoneError != null,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )
        if (phoneError != null) {
            Text(phoneError!!, color = MaterialTheme.colorScheme.error)
        }
        OutlinedButton(
            onClick = {
                val sanitized = PhoneNumbers.sanitize(phone)
                if (sanitized.isNotBlank() && !PhoneNumbers.isValid(sanitized)) {
                    phoneError = "Enter a valid number (10–15 digits)"
                    return@OutlinedButton
                }
                onSavePhone(sanitized)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save phone number")
        }

        Spacer(Modifier.height(8.dp))

        if (lockActive && !activeLockPin.isNullOrBlank()) {
            Text("Guard unlock code", fontWeight = FontWeight.SemiBold)
            Text(
                activeLockPin,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Also sent by SMS. Use Unlock below or type this on the Guard phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
        }

        Text("Actions", fontWeight = FontWeight.SemiBold)

        Button(
            onClick = { onSend(RemoteCommand.LOCK) },
            enabled = !sending,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Lock device")
        }
        OutlinedButton(
            onClick = { onSend(RemoteCommand.UNLOCK) },
            enabled = !sending,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Unlock")
        }

        if (statusMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(statusMessage, color = MaterialTheme.colorScheme.secondary)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "SMS keywords (from this Parent number only): PCA LOCK · PCA UNLOCK",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}
