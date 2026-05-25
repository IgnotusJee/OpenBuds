package dev.ignotus.openbuds

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.ignotus.openbuds.service.ControlCommand
import dev.ignotus.openbuds.service.DeviceStateSnapshot
import dev.ignotus.openbuds.service.SonyControlService
import dev.ignotus.openbuds.ui.screen.QuickPopupScreen

class QuickPopupActivity : ComponentActivity() {

    private val connectedBinder = mutableStateOf<SonyControlService.LocalBinder?>(null)
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bound = true
            connectedBinder.value = service as? SonyControlService.LocalBinder
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            connectedBinder.value = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bindService(
            Intent(this, SonyControlService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )

        setContent {
            val binder by connectedBinder
            var snapshot by remember { mutableStateOf(DeviceStateSnapshot.EMPTY) }

            // Bridge LiveData → Compose state. Uses Activity-lifecycle-aware observe(),
            // which auto-removes the observer when the Activity is destroyed.
            androidx.compose.runtime.DisposableEffect(binder) {
                binder?.state?.observe(this@QuickPopupActivity) { newValue ->
                    snapshot = newValue ?: DeviceStateSnapshot.EMPTY
                }
                onDispose { }
            }

            QuickPopupScreen(
                state = snapshot,
                onExecuteCommand = { command ->
                    binder?.execute(command) ?: false
                },
                onOpenFullApp = {
                    val intent = Intent(this@QuickPopupActivity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(intent)
                    finish()
                },
                onDismiss = { finish() },
            )
        }
    }

    override fun onDestroy() {
        if (bound) unbindService(connection)
        super.onDestroy()
    }
}
