package dev.ignotus.openbuds

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bind to service
        bindService(
            Intent(this, SonyControlService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )

        setContent {
            val snapshot = DeviceStateSnapshot.EMPTY // Phase 4: observe via LiveData

            QuickPopupScreen(
                state = snapshot,
                onExecuteCommand = { command ->
                    binder?.execute(command) ?: false
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
