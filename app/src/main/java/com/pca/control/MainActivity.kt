package com.pca.control

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.firestore.FirebaseFirestore
import com.pca.control.commands.CommandExecutor
import com.pca.control.commands.CommandSender
import com.pca.control.data.AppRole
import com.pca.control.data.LinkStatus
import com.pca.control.devicepolicy.DevicePolicyController
import com.pca.control.pairing.PairingState
import com.pca.control.ui.guard.GuardStatusScreen
import com.pca.control.ui.onboarding.GuardPairingScreen
import com.pca.control.ui.onboarding.ParentalLinkScreen
import com.pca.control.ui.onboarding.RoleSelectScreen
import com.pca.control.ui.parental.ParentalHomeScreen
import com.pca.control.ui.theme.PcaTheme
import com.pca.control.util.PhoneNumbers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var app: PcaApp
    private lateinit var policy: DevicePolicyController

    private val adminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* refreshed via recomposition */ }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as PcaApp
        policy = DevicePolicyController(this, app.preferences)

        val deepLinkCode = intent?.data?.getQueryParameter("code")

        setContent {
            PcaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val scope = rememberCoroutineScope()
                    val role by app.preferences.roleFlow.collectAsStateWithLifecycle(AppRole.NONE)
                    val linkStatus by app.preferences.linkStatusFlow.collectAsStateWithLifecycle(LinkStatus.UNLINKED)
                    val guardPhone by app.preferences.guardPhoneFlow.collectAsStateWithLifecycle("")
                    val lockActiveLocal by app.preferences.lockActiveFlow.collectAsStateWithLifecycle(false)

                    var pairCodeSent by remember { mutableStateOf(false) }
                    var statusFlow by remember { mutableStateOf<Flow<PairingState>?>(null) }
                    var error by remember { mutableStateOf<String?>(null) }
                    var busy by remember { mutableStateOf(false) }
                    var parentalStatus by remember { mutableStateOf<String?>(null) }
                    var launcherHidden by remember { mutableStateOf(false) }
                    var activeLockPin by remember { mutableStateOf<String?>(null) }
                    var lockActiveRemote by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        launcherHidden = app.preferences.isLauncherHidden()
                        CommandExecutor.startListeningIfGuard(applicationContext)
                        if (app.preferences.isLockActive() &&
                            app.preferences.getRole() == AppRole.GUARD
                        ) {
                            policy.launchLockUi(startLockTask = true)
                        }
                    }

                    // Parent listens for unlock PIN on own device doc
                    LaunchedEffect(role, linkStatus) {
                        if (role != AppRole.PARENTAL || linkStatus != LinkStatus.LINKED) {
                            lockActiveRemote = false
                            activeLockPin = null
                            return@LaunchedEffect
                        }
                        val deviceId = app.preferences.getDeviceId()
                        val reg = FirebaseFirestore.getInstance()
                            .collection("devices")
                            .document(deviceId)
                            .addSnapshotListener { snap, _ ->
                                if (snap != null && snap.exists()) {
                                    lockActiveRemote = snap.getBoolean("lockActive") == true
                                    activeLockPin = snap.getString("activeLockPin")
                                        ?.takeIf { it.isNotBlank() }
                                }
                            }
                        try {
                            awaitCancellation()
                        } finally {
                            reg.remove()
                        }
                    }

                    when {
                        role == AppRole.NONE -> RoleSelectScreen(
                            onParental = {
                                scope.launch {
                                    app.preferences.setRole(AppRole.PARENTAL)
                                }
                            },
                            onGuard = {
                                scope.launch {
                                    app.preferences.setRole(AppRole.GUARD)
                                }
                            }
                        )

                        role == AppRole.PARENTAL && linkStatus != LinkStatus.LINKED -> ParentalLinkScreen(
                            busy = busy,
                            error = error,
                            onLink = { code ->
                                scope.launch {
                                    busy = true
                                    error = null
                                    val result = app.pairingRepository.linkAsParent(
                                        code.ifBlank { deepLinkCode.orEmpty() }
                                    )
                                    busy = false
                                    result.onFailure { error = it.message }
                                    result.onSuccess {
                                        parentalStatus = "Linked to Guard"
                                    }
                                }
                            }
                        )

                        role == AppRole.PARENTAL -> ParentalHomeScreen(
                            guardPhone = guardPhone,
                            activeLockPin = activeLockPin,
                            lockActive = lockActiveRemote,
                            statusMessage = parentalStatus,
                            sending = busy,
                            onSavePhone = { phone ->
                                scope.launch {
                                    val sanitized = PhoneNumbers.sanitize(phone)
                                    app.preferences.setGuardPhone(sanitized)
                                    parentalStatus = if (sanitized.isBlank()) {
                                        "Guard phone cleared"
                                    } else {
                                        "Guard phone saved"
                                    }
                                }
                            },
                            onSend = { command ->
                                scope.launch {
                                    val saved = app.preferences.getGuardPhone()
                                    if (saved.isNotBlank() &&
                                        ContextCompat.checkSelfPermission(
                                            this@MainActivity,
                                            Manifest.permission.SEND_SMS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        permissionLauncher.launch(arrayOf(Manifest.permission.SEND_SMS))
                                    }
                                    busy = true
                                    parentalStatus = null
                                    val sender = CommandSender(this@MainActivity, app.preferences)
                                    val result = sender.send(command)
                                    busy = false
                                    parentalStatus = result.fold(
                                        onSuccess = {
                                            "Sent ${command.wire} via Firebase" +
                                                if (app.preferences.getGuardPhone().isNotBlank()) " + SMS" else ""
                                        },
                                        onFailure = { it.message ?: "Send failed" }
                                    )
                                }
                            }
                        )

                        role == AppRole.GUARD && linkStatus != LinkStatus.LINKED -> GuardPairingScreen(
                            codeSent = pairCodeSent,
                            statusFlow = statusFlow,
                            error = error,
                            onSendCode = { parentPhone ->
                                scope.launch {
                                    error = null
                                    if (ContextCompat.checkSelfPermission(
                                            this@MainActivity,
                                            Manifest.permission.SEND_SMS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        permissionLauncher.launch(arrayOf(Manifest.permission.SEND_SMS))
                                    }
                                    runCatching {
                                        val code = app.pairingRepository.startGuardPairing(
                                            this@MainActivity,
                                            parentPhone
                                        )
                                        pairCodeSent = true
                                        statusFlow = app.pairingRepository.observeGuardPairing(code)
                                    }.onFailure {
                                        error = it.message
                                            ?: "Could not start pairing. Check SMS permission and Firebase."
                                    }
                                }
                            },
                            onLinked = { peerId, pairId ->
                                scope.launch {
                                    app.pairingRepository.markGuardLinked(peerId, pairId)
                                    policy.hideLauncherIcon()
                                    launcherHidden = true
                                    CommandExecutor.startListeningIfGuard(applicationContext)
                                    requestGuardPermissions()
                                    requestBatteryOptimizationExemption()
                                }
                            },
                            onEnableAdmin = {
                                adminLauncher.launch(policy.requestAdminIntent())
                            },
                            isAdminActive = policy.isAdminActive(),
                            isDeviceOwner = policy.isDeviceOwner()
                        )

                        else -> GuardStatusScreen(
                            isAdminActive = policy.isAdminActive(),
                            isDeviceOwner = policy.isDeviceOwner(),
                            lockActive = lockActiveLocal,
                            launcherHidden = launcherHidden,
                            onEnableAdmin = {
                                adminLauncher.launch(policy.requestAdminIntent())
                            },
                            onRequestSmsPermission = { requestGuardPermissions() },
                            onDisableBatteryOptimization = { requestBatteryOptimizationExemption() }
                        )
                    }
                }
            }
        }
    }

    private fun requestGuardPermissions() {
        val needed = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= 33) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(android.os.PowerManager::class.java) ?: return
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        try {
            startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:$packageName")
                )
            )
        } catch (_: Exception) {
            try {
                startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                    )
                )
            } catch (_: Exception) {
            }
        }
    }
}
