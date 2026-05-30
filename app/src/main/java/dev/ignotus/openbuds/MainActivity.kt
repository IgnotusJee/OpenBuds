package dev.ignotus.openbuds

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dev.ignotus.openbuds.service.ControlCommand
import dev.ignotus.openbuds.service.DeviceStateSnapshot
import dev.ignotus.openbuds.service.SonyControlService

class MainActivity : ComponentActivity() {

    private val statusText by lazy { TextView(this).apply { textSize = 14f; setTextColor(Color.WHITE); setPadding(32, 32, 32, 32) } }
    private var serviceBinder: SonyControlService.LocalBinder? = null
    private var bound = false
    private val handler = Handler(Looper.getMainLooper())

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bound = true
            val binder = service as? SonyControlService.LocalBinder ?: return
            serviceBinder = binder
            binder.state.observe(this@MainActivity) { snapshot ->
                updateStatus(snapshot)
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            serviceBinder = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Basic dark background
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            gravity = Gravity.CENTER
        }

        val scroll = ScrollView(this).apply {
            addView(statusText)
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        // Refresh button
        val refreshBtn = Button(this).apply {
            text = "Refresh"
            setOnClickListener { serviceBinder?.execute(ControlCommand.Refresh) }
        }
        root.addView(refreshBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 32 })

        setContentView(root)

        requestPermissions()
        bindSonyService()

        handler.postDelayed({
            handleDebugIntent(intent)
        }, 500)
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

    private fun updateStatus(snapshot: DeviceStateSnapshot?) {
        val s = snapshot ?: DeviceStateSnapshot.EMPTY
        val sb = StringBuilder()
        sb.appendLine("OpenBuds")
        sb.appendLine("───────")
        sb.appendLine("Device: ${s.deviceName ?: "N/A"}")
        sb.appendLine("MAC: ${s.deviceMac ?: "N/A"}")
        sb.appendLine("Connected: ${if (s.isConnected) "Yes" else "No"}")
        sb.appendLine("Protocol: ${if (s.isProtocolReady) "Ready" else "Not ready"}")
        if (s.isConnected) {
            val bat = buildString {
                s.batteryLeft?.let { append("L:${it}% ") }
                s.batteryRight?.let { append("R:${it}% ") }
                s.batterySingle?.let { append("${it}% ") }
                s.batteryCradle?.let { append("Case:${it}%") }
            }
            if (bat.isNotBlank()) sb.appendLine("Battery: $bat")
            s.noiseControlMode?.let { sb.appendLine("NC: $it") }
            s.eqPresetName?.let { sb.appendLine("EQ: $it") }
        }
        statusText.text = sb.toString()
    }

    private fun handleDebugIntent(intent: Intent) {
        if (!isDebugBuild()) return
        val address = intent.getStringExtra(EXTRA_DEBUG_CONNECT_ADDRESS)
        val name = intent.getStringExtra(EXTRA_DEBUG_CONNECT_NAME) ?: "Sony audio device"
        if (!address.isNullOrBlank()) {
            statusText.append("\n\nDebug connect: $name ($address)")
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
