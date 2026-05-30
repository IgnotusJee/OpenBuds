package dev.ignotus.openbuds

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dev.ignotus.openbuds.data.SonyHeadphoneRepository
import dev.ignotus.openbuds.data.SonyHeadphoneUiState
import dev.ignotus.openbuds.service.ControlCommand
import dev.ignotus.openbuds.service.DeviceStateSnapshot
import dev.ignotus.openbuds.service.SonyControlService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: SonyHeadphoneRepository
    private var serviceBinder: SonyControlService.LocalBinder? = null
    private var bound = false
    private val handler = Handler(Looper.getMainLooper())

    // UI elements
    private lateinit var statusText: TextView
    private lateinit var scanStateText: TextView
    private lateinit var scanBtn: Button
    private lateinit var disconnectBtn: Button
    private lateinit var deviceListContainer: LinearLayout

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bound = true
            val binder = service as? SonyControlService.LocalBinder ?: return
            serviceBinder = binder
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            serviceBinder = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = SonyHeadphoneRepository.getInstance(applicationContext)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            setPadding(16, 16, 16, 16)
        }

        // ---- Status section ----
        statusText = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#CCCCCC"))
            typeface = Typeface.MONOSPACE
            setPadding(8, 8, 8, 8)
            text = "OpenBuds\n───────\nLoading..."
        }

        // ---- Scan state ----
        scanStateText = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#88AACC"))
            setPadding(8, 4, 8, 4)
            text = "Scan: Idle"
        }

        // ---- Buttons ----
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        scanBtn = Button(this).apply {
            text = "Start Scan"
            setOnClickListener { toggleScan() }
        }
        disconnectBtn = Button(this).apply {
            text = "Disconnect"
            visibility = View.GONE
            setOnClickListener { repository.disconnect() }
        }
        buttonRow.addView(scanBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 8 })
        buttonRow.addView(disconnectBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 8 })

        // ---- Device list header ----
        val deviceHeader = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#88CC88"))
            setPadding(8, 12, 8, 4)
            text = "Discovered devices:"
            typeface = Typeface.DEFAULT_BOLD
        }

        // ---- Device list ----
        deviceListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // ---- Assemble ----
        val scroll = ScrollView(this).apply {
            val inner = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(statusText)
                addView(scanStateText)
                addView(buttonRow)
                addView(deviceHeader)
                addView(deviceListContainer)
            }
            addView(inner)
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT
        ))

        setContentView(root)

        requestPermissions()
        bindSonyService()

        // Observe repository state
        lifecycleScope.launch {
            repository.state.collectLatest { state ->
                updateUi(state)
            }
        }

        // Handle debug intent
        handler.postDelayed({ handleDebugIntent(intent) }, 500)
    }

    private fun toggleScan() {
        if (repository.state.value.isScanning) {
            repository.stopScan()
        } else {
            repository.startScan()
        }
    }

    private fun updateUi(state: SonyHeadphoneUiState) {
        // Status section
        val sb = StringBuilder()
        sb.appendLine("OpenBuds")
        sb.appendLine("═══════")
        val dev = state.connectedDevice
        if (dev != null) {
            sb.appendLine("Connected: ${dev.name}")
            sb.appendLine("MAC: ${dev.address}")
            sb.appendLine("RSSI: ${dev.rssi}")
            sb.appendLine("Protocol: ${if (state.deviceInfo.protocolReady) "Ready" else "Not ready"}")
            sb.appendLine("Model: ${state.deviceInfo.modelName ?: "N/A"}")

            val bat = buildString {
                state.batteryState.single?.let { append("B:${it}% ") }
                state.batteryState.left?.let { append("L:${it}% ") }
                state.batteryState.right?.let { append("R:${it}% ") }
                state.batteryState.cradle?.let { append("C:${it}%") }
            }
            if (bat.isNotBlank()) sb.appendLine("Battery: $bat")
            state.noiseControlState.controlMode?.let { sb.appendLine("NC: $it") }
            state.eqState.preset?.let { sb.appendLine("EQ: $it") }
        } else {
            sb.appendLine("Status: Disconnected")
            if (state.permissionIssue != null) {
                sb.appendLine("Permission: ${state.permissionIssue}")
            }
        }
        statusText.text = sb.toString()

        // Scan state
        scanStateText.text = if (state.isScanning) "Scan: Active (${state.discoveredDevices.size} found)"
        else "Scan: Idle (${state.knownDevices.size} known)"
        scanBtn.text = if (state.isScanning) "Stop Scan" else "Start Scan"

        // Disconnect button
        disconnectBtn.visibility = if (dev != null) View.VISIBLE else View.GONE

        // Device list
        deviceListContainer.removeAllViews()
        val devices = if (state.isScanning || state.discoveredDevices.isNotEmpty()) {
            state.discoveredDevices
        } else {
            state.knownDevices
        }
        for (device in devices.distinctBy { it.address }.take(15)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 4, 0, 4)
                gravity = Gravity.CENTER_VERTICAL
            }
            val label = TextView(this).apply {
                textSize = 12f
                setTextColor(Color.parseColor("#AAAAAA"))
                text = buildString {
                    append(device.name.take(28))
                    if (device.isLikelyControlEndpoint) append(" ✓")
                    device.sonyAd?.let { append(" [Sony v${it.version}]") }
                    append("\n${device.address}  RSSI:${device.rssi}")
                }
            }
            row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            if (state.connectedDevice?.address != device.address) {
                val connectBtn = Button(this).apply {
                    textSize = 11f
                    text = "Connect"
                    setOnClickListener {
                        repository.connect(device.address, device.name)
                    }
                }
                row.addView(connectBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            } else {
                val connectedLabel = TextView(this).apply {
                    textSize = 11f
                    setTextColor(Color.parseColor("#88CC88"))
                    text = "Connected"
                    setPadding(12, 0, 0, 0)
                }
                row.addView(connectedLabel)
            }

            deviceListContainer.addView(row)
        }

        if (devices.isEmpty() && !state.isScanning) {
            val emptyHint = TextView(this).apply {
                textSize = 12f
                setTextColor(Color.parseColor("#666666"))
                text = "No devices found. Tap 'Start Scan' to search for headphones."
                setPadding(8, 8, 8, 8)
            }
            deviceListContainer.addView(emptyHint)
        }
    }

    private fun requestPermissions() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
            .toTypedArray()

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions, 0)
        }
    }

    private fun bindSonyService() {
        bindService(
            Intent(this, SonyControlService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
    }

    private fun handleDebugIntent(intent: Intent) {
        if (!isDebugBuild()) return
        val address = intent.getStringExtra(EXTRA_DEBUG_CONNECT_ADDRESS)
        val name = intent.getStringExtra(EXTRA_DEBUG_CONNECT_NAME) ?: "Sony audio device"
        if (!address.isNullOrBlank()) {
            repository.connect(address, name)
        }
        val action = intent.getStringExtra(EXTRA_DEBUG_ACTION)
        if (!action.isNullOrBlank() && repository.state.value.deviceInfo.protocolReady) {
            repository.runDebugAction(action, intent.getStringExtra(EXTRA_DEBUG_RAW_HEX))
        }
    }

    override fun onDestroy() {
        if (bound) unbindService(connection)
        super.onDestroy()
    }

    private fun isDebugBuild(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    companion object {
        const val EXTRA_DEBUG_CONNECT_ADDRESS = "debug_connect_address"
        const val EXTRA_DEBUG_CONNECT_NAME = "debug_connect_name"
        const val EXTRA_DEBUG_ACTION = "debug_action"
        const val EXTRA_DEBUG_RAW_HEX = "debug_raw_hex"
    }
}
