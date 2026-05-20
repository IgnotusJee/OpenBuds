package dev.ignotus.sonyrebuild

import android.Manifest
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import dev.ignotus.sonyrebuild.data.SonyHeadphoneRepository
import dev.ignotus.sonyrebuild.theme.SonyRebuildTheme
import dev.ignotus.sonyrebuild.ui.SonyRebuildApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        configureInitialSystemBars()
        setContent {
            val repository = remember {
                SonyHeadphoneRepository(applicationContext)
            }
            val state by repository.state.collectAsStateWithLifecycle()
            var debugActionRan by remember { mutableStateOf(false) }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { /* Repository reports missing permissions when actions are attempted. */ }
            val permissions = remember {
                buildList {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        add(Manifest.permission.BLUETOOTH_SCAN)
                        add(Manifest.permission.BLUETOOTH_CONNECT)
                    } else {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }.toTypedArray()
            }

            LaunchedEffect(Unit) {
                permissionLauncher.launch(permissions)
            }
            LaunchedEffect(Unit) {
                if (isDebugBuild()) {
                    val address = intent.getStringExtra(EXTRA_DEBUG_CONNECT_ADDRESS)
                    val name = intent.getStringExtra(EXTRA_DEBUG_CONNECT_NAME) ?: "Sony audio device"
                    if (!address.isNullOrBlank()) {
                        delay(1_000)
                        repository.connect(address, name)
                    }
                }
            }
            LaunchedEffect(state.deviceInfo.protocolReady) {
                if (isDebugBuild() && state.deviceInfo.protocolReady && !debugActionRan) {
                    val action = intent.getStringExtra(EXTRA_DEBUG_ACTION)
                    if (!action.isNullOrBlank()) {
                        debugActionRan = true
                        delay(5_000)
                        repository.runDebugAction(action, intent.getStringExtra(EXTRA_DEBUG_RAW_HEX))
                    }
                }
            }

            SonyRebuildTheme(configureSystemBars = false) {
                SonyRebuildApp(
                    state = state,
                    onStartScan = repository::startScan,
                    onStopScan = repository::stopScan,
                    onConnect = repository::connect,
                    onDisconnect = repository::disconnect,
                    onRefresh = repository::refreshBasics,
                    onSetNoiseControlMode = repository::setNoiseControlMode,
                    onSetAmbientLevel = repository::setAmbientLevel,
                    onSetAmbientVoiceMode = repository::setAmbientVoiceMode,
                    onSetEqPreset = repository::setEqPreset,
                    onSetClearBass = repository::setClearBass,
                    onSetCustomEqBand = repository::setCustomEqBand,
                    onPlaybackPrevious = repository::playbackPrevious,
                    onPlaybackPlayPause = repository::playbackPlayPause,
                    onPlaybackNext = repository::playbackNext,
                    onDebugLoggingChanged = repository::setDebugLogging,
                    onAutoReconnectChanged = repository::setAutoReconnect,
                    onStrictScanFilterChanged = repository::setStrictSonyScanFilter,
                )
            }
        }
    }

    companion object {
        const val EXTRA_DEBUG_CONNECT_ADDRESS = "debug_connect_address"
        const val EXTRA_DEBUG_CONNECT_NAME = "debug_connect_name"
        const val EXTRA_DEBUG_ACTION = "debug_action"
        const val EXTRA_DEBUG_RAW_HEX = "debug_raw_hex"
    }

    private fun isDebugBuild(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun configureInitialSystemBars() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
    }
}
