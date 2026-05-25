package dev.ignotus.openbuds.lsposed

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.util.Log

class HyperOsBatteryNotification : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        when (intent.action) {
            CrossProcessActions.ACTION_UPDATE_HYPEROS_NOTIFICATION -> {
                val deviceName = intent.getStringExtra(CrossProcessActions.EXTRA_DEVICE_NAME)
                    ?: return
                val deviceMac = intent.getStringExtra(CrossProcessActions.EXTRA_DEVICE_MAC)
                    ?: return
                val isConnected = intent.getBooleanExtra(
                    CrossProcessActions.EXTRA_IS_CONNECTED, false
                )

                if (!isConnected) {
                    cancelNotification(context, deviceMac)
                    return
                }

                val batterySingle = intent.getIntExtra(
                    CrossProcessActions.EXTRA_BATTERY_SINGLE, -1
                )
                val batteryLeft = intent.getIntExtra(
                    CrossProcessActions.EXTRA_BATTERY_LEFT, -1
                )
                val batteryRight = intent.getIntExtra(
                    CrossProcessActions.EXTRA_BATTERY_RIGHT, -1
                )
                val batteryCradle = intent.getIntExtra(
                    CrossProcessActions.EXTRA_BATTERY_CRADLE, -1
                )

                createOrUpdateNotification(
                    context, deviceName, deviceMac,
                    batterySingle, batteryLeft, batteryRight, batteryCradle,
                )
            }
            CrossProcessActions.ACTION_CANCEL_HYPEROS_NOTIFICATION -> {
                val deviceMac = intent.getStringExtra(CrossProcessActions.EXTRA_DEVICE_MAC)
                    ?: return
                cancelNotification(context, deviceMac)
            }
        }
    }

    private fun createOrUpdateNotification(
        context: Context,
        deviceName: String,
        deviceMac: String,
        batterySingle: Int,
        batteryLeft: Int,
        batteryRight: Int,
        batteryCradle: Int,
    ) {
        try {
            val res = context.resources
            val boxStrId = res.getIdentifier(
                "miheadset_notification_Box", "string", "com.xiaomi.bluetooth"
            )
            val leftStrId = res.getIdentifier(
                "miheadset_notification_LeftEar", "string", "com.xiaomi.bluetooth"
            )
            val rightStrId = res.getIdentifier(
                "miheadset_notification_RightEar", "string", "com.xiaomi.bluetooth"
            )
            val disconnectStrId = res.getIdentifier(
                "miheadset_notification_Disconnect", "string", "com.xiaomi.bluetooth"
            )
            val accentColorId = res.getIdentifier(
                "system_notification_accent_color", "color", "android"
            )
            val headsetIconId = res.getIdentifier(
                "ic_headset_notification", "drawable", "com.xiaomi.bluetooth"
            )

            val channelId = "BTHeadset$deviceMac"

            val batteryText = buildBatteryText(
                res, boxStrId, leftStrId, rightStrId,
                batterySingle, batteryLeft, batteryRight, batteryCradle,
            )

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        deviceName,
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { setShowBadge(false) }
                )
            }

            val disconnectLabel = if (disconnectStrId != 0) {
                res.getString(disconnectStrId)
            } else {
                "Disconnect"
            }

            val accentColor = if (accentColorId != 0) {
                context.getColor(accentColorId)
            } else {
                context.getColor(android.R.color.holo_blue_light)
            }

            val disconnectBundle = Bundle().apply {
                putParcelable("Device", null) // Device parcelable not available; system uses MAC
            }
            val disconnectIntent = Intent("com.android.bluetooth.headset.notification").apply {
                putExtra("btData", disconnectBundle)
                putExtra("disconnect", "1")
                setIdentifier(channelId)
            }

            val extras = Bundle().apply {
                putBoolean("miui.showAction", true)
                if (headsetIconId != 0) {
                    putParcelable(
                        "miui.appIcon",
                        Icon.createWithResource(context, headsetIconId)
                    )
                }
            }

            val notification = Notification.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setWhen(0L)
                .setContentTitle(deviceName)
                .setContentText(batteryText)
                .setColor(accentColor)
                .setContentIntent(
                    PendingIntent.getBroadcast(
                        context, 0, disconnectIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                )
                .setDeleteIntent(
                    PendingIntent.getBroadcast(
                        context, 0,
                        Intent("com.android.bluetooth.headset.notification.cancle").apply {
                            putExtra("android.bluetooth.device.extra.DEVICE", deviceMac)
                        },
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                )
                .addAction(
                    Notification.Action(
                        285737079,
                        disconnectLabel,
                        PendingIntent.getBroadcast(
                            context, 0, disconnectIntent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        )
                    )
                )
                .setExtras(extras)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .build()

            notifyViaSystemUi(nm, channelId, NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create HyperOS notification", e)
        }
    }

    private fun buildBatteryText(
        res: android.content.res.Resources,
        boxStrId: Int,
        leftStrId: Int,
        rightStrId: Int,
        batterySingle: Int,
        batteryLeft: Int,
        batteryRight: Int,
        batteryCradle: Int,
    ): String {
        val sb = StringBuilder()

        val hasTws = batteryLeft >= 0 || batteryRight >= 0

        if (hasTws) {
            // TWS earbuds: show case, left, right
            if (batteryCradle >= 0) {
                val caseLabel = if (boxStrId != 0) res.getString(boxStrId) else "Case"
                sb.append("$caseLabel: $batteryCradle%")
            }
            if (batteryLeft >= 0 || batteryRight >= 0) {
                if (sb.isNotEmpty()) sb.append("\n")
                if (batteryLeft >= 0) {
                    val leftLabel = if (leftStrId != 0) res.getString(leftStrId) else "L"
                    sb.append("$leftLabel: $batteryLeft%")
                }
                if (batteryLeft >= 0 && batteryRight >= 0) sb.append(" | ")
                if (batteryRight >= 0) {
                    val rightLabel = if (rightStrId != 0) res.getString(rightStrId) else "R"
                    sb.append("$rightLabel: $batteryRight%")
                }
            }
        } else if (batterySingle >= 0) {
            // Headset: single battery
            sb.append("Battery: $batterySingle%")
        }

        return sb.ifEmpty { "Connected" }.toString()
    }

    private fun cancelNotification(context: Context, deviceMac: String) {
        try {
            val channelId = "BTHeadset$deviceMac"
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            cancelViaSystemUi(nm, channelId, NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel HyperOS notification", e)
        }
    }

    private fun notifyViaSystemUi(
        nm: NotificationManager,
        tag: String,
        id: Int,
        notification: Notification,
    ) {
        try {
            val userHandle = UserHandle::class.java.getDeclaredField("ALL")
                .get(null) as? UserHandle
            if (userHandle != null) {
                val method = NotificationManager::class.java.getDeclaredMethod(
                    "notifyAsUser",
                    String::class.java,
                    Int::class.javaPrimitiveType ?: Int::class.javaObjectType,
                    Notification::class.java,
                    UserHandle::class.java,
                )
                method.invoke(nm, tag, id, notification, userHandle)
                return
            }
        } catch (_: Exception) {
            // Fall back to standard notify
        }
        nm.notify(tag, id, notification)
    }

    private fun cancelViaSystemUi(
        nm: NotificationManager,
        tag: String,
        id: Int,
    ) {
        try {
            val userHandle = UserHandle::class.java.getDeclaredField("ALL")
                .get(null) as? UserHandle
            if (userHandle != null) {
                val method = NotificationManager::class.java.getDeclaredMethod(
                    "cancelAsUser",
                    String::class.java,
                    Int::class.javaPrimitiveType ?: Int::class.javaObjectType,
                    UserHandle::class.java,
                )
                method.invoke(nm, tag, id, userHandle)
                return
            }
        } catch (_: Exception) {
            // Fall back to standard cancel
        }
        nm.cancel(tag, id)
    }

    companion object {
        private const val TAG = "OpenBuds"
        private const val NOTIFICATION_ID = 10003
    }
}
