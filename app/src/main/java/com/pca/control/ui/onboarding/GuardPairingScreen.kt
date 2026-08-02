package com.pca.control.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pca.control.pairing.PairingState
import com.pca.control.util.PhoneNumbers
import kotlinx.coroutines.flow.Flow

@Composable
fun GuardPairingScreen(
    codeSent: Boolean,
    statusFlow: Flow<PairingState>?,
    error: String?,
    onSendCode: (parentPhone: String) -> Unit,
    onLinked: (peerId: String, pairId: String) -> Unit,
    onEnableAdmin: () -> Unit,
    isAdminActive: Boolean,
    isDeviceOwner: Boolean
) {
    var parentPhone by remember { mutableStateOf("") }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf("Waiting for Parent to enter the code…") }

    LaunchedEffect(statusFlow) {
        statusFlow?.collect { state ->
            when (state) {
                is PairingState.Waiting -> statusText = "Code sent. Waiting for Parent…"
                is PairingState.Linked -> {
                    statusText = "Linked!"
                    onLinked(state.peerDeviceId, state.pairId)
                }
                is PairingState.Error -> statusText = state.message
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Guard setup",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Enter the Parent phone number. A pairing code is sent by SMS — it is not shown here.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(24.dp))

        if (!codeSent) {
            OutlinedTextField(
                value = parentPhone,
                onValueChange = {
                    parentPhone = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' || ch == '-' }
                        .take(20)
                    phoneError = null
                },
                label = { Text("Parent phone number") },
                placeholder = { Text("+919004875711") },
                supportingText = {
                    Text("Include country code when possible")
                },
                singleLine = true,
                isError = phoneError != null,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            if (phoneError != null) {
                Spacer(Modifier.height(4.dp))
                Text(phoneError!!, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (!PhoneNumbers.isValid(parentPhone)) {
                        phoneError = "Enter a valid number (10–15 digits)"
                        return@Button
                    }
                    onSendCode(parentPhone)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Send pairing code")
            }
        } else {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(12.dp))
            Text(statusText, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (!PhoneNumbers.isValid(parentPhone) && parentPhone.isNotBlank()) {
                        phoneError = "Enter a valid number (10–15 digits)"
                        return@Button
                    }
                    // Allow resend with same or new number via field if shown again —
                    // for resend we keep previous number in prefs; reopen field:
                },
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Waiting for Parent…")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = parentPhone,
                onValueChange = {
                    parentPhone = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' || ch == '-' }
                        .take(20)
                },
                label = { Text("Parent phone (resend)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (!PhoneNumbers.isValid(parentPhone)) {
                        phoneError = "Enter a valid number (10–15 digits)"
                        return@Button
                    }
                    onSendCode(parentPhone)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Send new code")
            }
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(24.dp))
        if (!isAdminActive) {
            Button(onClick = onEnableAdmin, modifier = Modifier.fillMaxWidth()) {
                Text("Enable device admin (required)")
            }
        } else {
            Text(
                if (isDeviceOwner) "Device Owner active — uninstall blocked"
                else "Device Admin active. For hard lock / uninstall protection run ADB device-owner (see README).",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
