package dev.ignotus.openbuds.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dev.ignotus.openbuds.MainActivity
import dev.ignotus.openbuds.data.HeadphoneRepository
import dev.ignotus.openbuds.lsposed.milink.MilinkAirpodsTargetMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SonyControlService : Service() {

    private lateinit var repository: HeadphoneRepository
    private val binder = LocalBinder()
    private val stateLiveData = MutableLiveData(DeviceStateSnapshot.EMPTY)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    inner class LocalBinder : Binder() {
        val state: LiveData<DeviceStateSnapshot> get() = stateLiveData
        fun execute(command: ControlCommand): Boolean {
            if (!stateLiveData.value!!.isProtocolReady && command !is ControlCommand.Refresh) {
                Log.w(TAG, "Command ignored: protocol not ready")
                return false
            }
            return when (command) {
                is ControlCommand.SetNoiseControl -> {
                    repository.setNoiseControlMode(command.mode)
                    true
                }
                is ControlCommand.SetAmbientLevel -> {
                    repository.setAmbientLevel(command.level)
                    true
                }
                is ControlCommand.SetAmbientVoiceMode -> {
                    repository.setAmbientVoiceMode(command.enabled)
                    true
                }
                is ControlCommand.Playback -> {
                    when (command.action) {
                        ControlCommand.PlaybackAction.PREVIOUS -> repository.playbackPrevious()
                        ControlCommand.PlaybackAction.PLAY_PAUSE -> repository.playbackPlayPause()
                        ControlCommand.PlaybackAction.NEXT -> repository.playbackNext()
                    }
                    true
                }
                is ControlCommand.Refresh -> {
                    repository.refreshBasics()
                    true
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = HeadphoneRepository.getInstance(this)
        startForeground(NOTIFICATION_ID, createNotification(DeviceStateSnapshot.EMPTY))

        serviceScope.launch {
            repository.state.collect { uiState ->
                val snapshot = DeviceStateSnapshot.fromUiState(uiState)
                stateLiveData.postValue(snapshot)
                updateNotification(snapshot)
                Companion.currentDeviceMac = snapshot.deviceMac
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> repository.disconnect()
            ACTION_AUTO_CONNECT -> autoConnect()
        }
        return START_STICKY
    }

    /**
     * Scans bonded Bluetooth devices and auto-connects to the first
     * known-profile headphone (Sony BLE, QCY, etc.) that is currently
     * connected at the ACL level.
     *
     * Called when [MilinkBridgeService] starts (triggered by milink binding)
     * so that real protocol data is available before milink queries.
     */
    private fun autoConnect() {
        if (repository.state.value.connectedDevice != null) return
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            @Suppress("DEPRECATION")
            val bonded = try {
                adapter.bondedDevices
            } catch (_: SecurityException) {
                return
            }

            val target = bonded.firstOrNull { device ->
                isKnownHeadphone(device.name ?: "")
            } ?: return

            Log.i(TAG, "Auto-connecting via BLE scan to: ${target.name} (${target.address})")
            // Do a brief BLE scan to get proper SonyAd discovery data, then auto-connect.
            // Direct connect(address, name) without scan data picks wrong SPP UUIDs for
            // some devices; scanning first provides the full advertisement data that
            // Sony Tandem transport needs for correct SPP path selection.
            repository.startScan()
            handler.postDelayed({
                repository.stopScan()
                if (repository.state.value.connectedDevice == null
                    && repository.state.value.discoveredDevices.isNotEmpty()
                ) {
                    val discovered = repository.state.value.discoveredDevices
                        .firstOrNull { d -> MilinkAirpodsTargetMatcher.normalizeMac(d.address) != null }
                    if (discovered != null) {
                        Log.i(TAG, "Auto-connecting discovered: ${discovered.name} (${discovered.address})")
                        repository.connect(discovered)
                    }
                }
            }, AUTO_CONNECT_SCAN_MS)
        } catch (e: Exception) {
            Log.w(TAG, "Auto-connect failed", e)
        }
    }

    private fun isKnownHeadphone(name: String): Boolean {
        val n = name.lowercase()
        return KNOWN_PATTERNS.any { n.contains(it) }
    }

    override fun onDestroy() {
        repository.disconnect()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun updateNotification(snapshot: DeviceStateSnapshot) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, createNotification(snapshot))
    }

    private fun createNotification(snapshot: DeviceStateSnapshot): Notification {
        createChannelIfNeeded()

        val connected = snapshot.isConnected
        val title = snapshot.deviceName ?: "Sony Headphones"
        val content = if (connected) buildString {
            snapshot.batteryLeft?.let { append("L:$it% ") }
            snapshot.batteryRight?.let { append("R:$it% ") }
            snapshot.batteryCradle?.let { append("Case:$it%") }
            if (isBlank()) append(snapshot.noiseControlMode?.name ?: "Connected")
        } else "Disconnected"

        val disconnectIntent = Intent(this, SonyControlService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPending = PendingIntent.getService(
            this, 1, disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val mainPending = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(mainPending)
            .setOngoing(connected)
            .apply { if (connected) addAction(0, "Disconnect", disconnectPending) }
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Sony Headphone Service",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { setShowBadge(false) }
                )
            }
        }
    }

    companion object {
        private const val TAG = "OpenBuds"
        private const val CHANNEL_ID = "sony_control_service"
        private const val NOTIFICATION_ID = 2001
        const val ACTION_DISCONNECT = "dev.ignotus.openbuds.action.DISCONNECT"
        const val ACTION_AUTO_CONNECT = "dev.ignotus.openbuds.action.AUTO_CONNECT"

        /** Known headphone name patterns that OpenBuds has transport support for. */
        private val KNOWN_PATTERNS = setOf(
            "linkbuds", "wh-", "wf-", "wi-", "mdr-",  // Sony BLE/Tandem
            "qcy",                                         // QCY BLE
        )
        private const val AUTO_CONNECT_SCAN_MS = 4_000L

        @Volatile
        var currentDeviceMac: String? = null
            private set
    }
}
