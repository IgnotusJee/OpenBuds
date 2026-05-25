package dev.ignotus.openbuds

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.ignotus.openbuds.service.ControlCommand
import dev.ignotus.openbuds.service.DeviceStateSnapshot
import dev.ignotus.openbuds.service.SonyControlService
import dev.ignotus.openbuds.theme.OpenBudsTheme
import dev.ignotus.openbuds.ui.AppColorMode
import dev.ignotus.openbuds.ui.AppUiSettingsStore
import dev.ignotus.openbuds.ui.resolveDarkTheme
import dev.ignotus.openbuds.ui.screen.QuickPopupScreen

class QuickPopupActivity : ComponentActivity() {

    private val currentSnapshot = mutableStateOf(DeviceStateSnapshot.EMPTY)
    private var bound = false
    private var serviceBinder: SonyControlService.LocalBinder? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bound = true
            val binder = service as? SonyControlService.LocalBinder ?: return
            serviceBinder = binder
            binder.state.observe(this@QuickPopupActivity) { newValue ->
                currentSnapshot.value = newValue ?: DeviceStateSnapshot.EMPTY
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            serviceBinder = null
            currentSnapshot.value = DeviceStateSnapshot.EMPTY
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bindService(
            Intent(this, SonyControlService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )

        val settingsStore = AppUiSettingsStore(applicationContext)

        setContent {
            val snapshot by currentSnapshot
            val appUiSettings by settingsStore.settings.collectAsState(initial = null)
            val loaded = appUiSettings
            val darkTheme = if (loaded != null) {
                resolveDarkTheme(
                    remember(loaded.colorMode) {
                        try { AppColorMode.valueOf(loaded.colorMode) }
                        catch (_: IllegalArgumentException) { AppColorMode.System }
                    },
                    isSystemInDarkTheme(),
                )
            } else {
                isSystemInDarkTheme()
            }

            OpenBudsTheme(darkTheme = darkTheme, configureSystemBars = false) {
                QuickPopupScreen(
                    state = snapshot,
                    onExecuteCommand = { command ->
                        serviceBinder?.execute(command) ?: false
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
    }

    override fun onDestroy() {
        if (bound) unbindService(connection)
        super.onDestroy()
    }
}
