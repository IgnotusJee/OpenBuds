package dev.ignotus.openbuds.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dev.ignotus.openbuds.MainActivity
import dev.ignotus.openbuds.data.SonyHeadphoneRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SonyControlService : Service() {

    private lateinit var repository: SonyHeadphoneRepository
    private val binder = LocalBinder()
    private val stateLiveData = MutableLiveData(DeviceStateSnapshot.EMPTY)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    inner class LocalBinder : Binder() {
        val state: LiveData<DeviceStateSnapshot> = stateLiveData
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
        repository = SonyHeadphoneRepository(this)
        startForeground(NOTIFICATION_ID, createNotification(DeviceStateSnapshot.EMPTY))

        serviceScope.launch {
            repository.state.collect { uiState ->
                val snapshot = DeviceStateSnapshot.fromUiState(uiState)
                stateLiveData.postValue(snapshot)
                updateNotification(snapshot)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> repository.disconnect()
            ACTION_SHOW_POPUP -> {
                val popupIntent = Intent(ACTION_SHOW_POPUP).apply {
                    setClassName(
                        this@SonyControlService,
                        "dev.ignotus.openbuds.QuickPopupActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(popupIntent)
            }
        }
        return START_STICKY
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

        val popupIntent = Intent(ACTION_SHOW_POPUP).apply {
            setClassName(this@SonyControlService, "dev.ignotus.openbuds.QuickPopupActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val popupPending = PendingIntent.getActivity(
            this, 2, popupIntent,
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
            .addAction(0, "Popup", popupPending)
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
        const val ACTION_SHOW_POPUP = "dev.ignotus.openbuds.action.SHOW_QUICK_POPUP"
    }
}
