package com.pca.control.ui.onboarding

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pca.control.pairing.PairingState
import com.pca.control.util.QrUtils
import kotlinx.coroutines.flow.Flow

@Composable
fun GuardPairingScreen(
    code: String?,
    qrPayload: String?,
    statusFlow: Flow<PairingState>?,
    error: String?,
    onGenerate: () -> Unit,
    onLinked: (peerId: String, pairId: String) -> Unit,
    onEnableAdmin: () -> Unit,
    isAdminActive: Boolean,
    isDeviceOwner: Boolean
) {
    var statusText by remember { mutableStateOf("Waiting for Parental to enter the code…") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(qrPayload) {
        qrBitmap = qrPayload?.let { QrUtils.encode(it) }
    }

    LaunchedEffect(statusFlow) {
        statusFlow?.collect { state ->
            when (state) {
                is PairingState.Waiting -> statusText = "Waiting for Parental to enter the code…"
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
            "Show this code or QR on the Parental phone.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(24.dp))

        if (code.isNullOrBlank()) {
            Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
                Text("Generate pairing code")
            }
        } else {
            Text(
                code,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            qrBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Pairing QR",
                    modifier = Modifier.size(220.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(statusText, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
                Text("Generate new code")
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
                else "Device Admin active. For uninstall protection run ADB device-owner (see README).",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
