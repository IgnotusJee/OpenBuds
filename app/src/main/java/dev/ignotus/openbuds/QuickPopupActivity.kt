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

    private var binder: SonyControlService.LocalBinder? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder = service as? SonyControlService.LocalBinder
            bound = true
            // trigger recomposition with the now-available binder
            _connectedBinder = binder
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
            bound = false
            _connectedBinder = null
        }
    }

    @Volatile
    private var _connectedBinder: SonyControlService.LocalBinder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bindService(
            Intent(this, SonyControlService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )

        setContent {
            var currentBinder by remember { mutableStateOf(_connectedBinder) }

            // Observe LiveData from the connected binder
            val snapshot = remember(currentBinder) {
                currentBinder?.state?.let { liveData ->
                    object {
                        var value by mutableStateOf(liveData.value ?: DeviceStateSnapshot.EMPTY)
                    }.also { observer ->
                        liveData.observe(this@QuickPopupActivity) { newValue ->
                            observer.value = newValue ?: DeviceStateSnapshot.EMPTY
                        }
                    }
                }
            }

            QuickPopupScreen(
                state = snapshot?.value ?: DeviceStateSnapshot.EMPTY,
                onExecuteCommand = { command ->
                    currentBinder?.execute(command) ?: false
                },
                onOpenFullApp = {
                    val intent = Intent(this, MainActivity::class.java).apply {
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
