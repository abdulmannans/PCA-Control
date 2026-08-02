package com.pca.control

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.pca.control.devicepolicy.DevicePolicyController
import com.pca.control.lock.LockNotifications
import com.pca.control.ui.theme.PcaTheme
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class LockActivity : ComponentActivity() {

    private lateinit var policy: DevicePolicyController
    private var sticky = true
    private var lastReassertAt = 0L

    private val clearReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DevicePolicyController.ACTION_LOCK_CLEARED) {
                sticky = false
                isResumedFlag.set(false)
                policy.stopLockTaskIfNeeded(this@LockActivity)
                LockNotifications.cancelFullScreenLock(this@LockActivity)
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PcaApp
        policy = DevicePolicyController(this, app.preferences)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        applyImmersive()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Consume — Guard cannot leave lock via Back
            }
        })

        val filter = IntentFilter(DevicePolicyController.ACTION_LOCK_CLEARED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(clearReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(clearReceiver, filter)
        }

        // Hard kiosk only with Device Owner (no optional pin prompt)
        if (intent.getBooleanExtra(EXTRA_START_LOCK_TASK, false) && policy.isDeviceOwner()) {
            policy.enableLockTask(this)
        }

        setContent {
            PcaTheme {
                var pin by remember { mutableStateOf("") }
                var error by remember { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Device locked",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Enter the 6-digit code from your Parent to unlock.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(28.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(6) { i ->
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (i < pin.length) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                                    )
                            )
                        }
                    }
                    if (error != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(28.dp))
                    Keypad(
                        onDigit = { d ->
                            if (pin.length < 6) {
                                val next = pin + d
                                pin = next
                                error = null
                                if (next.length == 6) {
                                    scope.launch {
                                        val ok = policy.unlockWithPin(next)
                                        if (ok) {
                                            sticky = false
                                            isResumedFlag.set(false)
                                            policy.stopLockTaskIfNeeded(this@LockActivity)
                                            LockNotifications.cancelFullScreenLock(this@LockActivity)
                                            finish()
                                        } else {
                                            error = "Incorrect code"
                                            pin = ""
                                        }
                                    }
                                }
                            }
                        },
                        onDelete = {
                            if (pin.isNotEmpty()) pin = pin.dropLast(1)
                            error = null
                        },
                        onClear = {
                            pin = ""
                            error = null
                        }
                    )
                }
            }
        }
    }

    private fun applyImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    override fun onResume() {
        super.onResume()
        isResumedFlag.set(true)
        LockNotifications.cancelFullScreenLock(this)
        applyImmersive()
        lifecycleScope.launch {
            val app = application as PcaApp
            if (!app.preferences.isLockActive()) {
                sticky = false
                isResumedFlag.set(false)
                finish()
            } else if (policy.isDeviceOwner() && !isInLockTaskMode()) {
                policy.enableLockTask(this@LockActivity)
            }
        }
    }

    override fun onPause() {
        isResumedFlag.set(false)
        super.onPause()
        reassertLock()
    }

    override fun onStop() {
        isResumedFlag.set(false)
        super.onStop()
        reassertLock()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        reassertLock()
    }

    private fun reassertLock() {
        if (!sticky) return
        val now = System.currentTimeMillis()
        if (now - lastReassertAt < 800L) return
        lastReassertAt = now
        lifecycleScope.launch {
            val app = application as PcaApp
            if (app.preferences.isLockActive()) {
                policy.launchLockUiImmediate(startLockTask = true)
                // Prefer activity; only notify if still not up
                kotlinx.coroutines.delay(350)
                if (!isResumedFlag.get()) {
                    LockNotifications.postFullScreenLock(this@LockActivity)
                }
            }
        }
    }

    private fun isInLockTaskMode(): Boolean {
        val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        return am.lockTaskModeState != android.app.ActivityManager.LOCK_TASK_MODE_NONE
    }

    override fun onDestroy() {
        isResumedFlag.set(false)
        runCatching { unregisterReceiver(clearReceiver) }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_START_LOCK_TASK = "start_lock_task"
        private val isResumedFlag = AtomicBoolean(false)
        val isResumedNow: Boolean get() = isResumedFlag.get()
    }
}

@Composable
private fun Keypad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("C", "0", "⌫")
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { key ->
                    when (key) {
                        "C" -> TextButton(onClick = onClear, modifier = Modifier.size(72.dp)) {
                            Text("C", fontSize = 20.sp)
                        }
                        "⌫" -> TextButton(onClick = onDelete, modifier = Modifier.size(72.dp)) {
                            Text("⌫", fontSize = 20.sp)
                        }
                        else -> Button(
                            onClick = { onDigit(key) },
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape
                        ) {
                            Text(key, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
