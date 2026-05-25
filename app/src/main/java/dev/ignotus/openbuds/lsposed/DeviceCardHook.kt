package dev.ignotus.openbuds.lsposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
import dev.ignotus.openbuds.QuickPopupActivity
import io.github.libxposed.api.XposedInterface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DeviceCardHook(private val pluginClassLoader: ClassLoader) {

    @Volatile
    var panelController: Any? = null

    fun hook() {
        try {
            val mainPanelClass = pluginClassLoader.loadClass(
                "miui.systemui.controlcenter.panel.main.MainPanelController"
            )
            val onCreateMethod = mainPanelClass.getDeclaredMethod("onCreate")
            ModuleMain.instance.hook(onCreateMethod)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val result = chain.proceed()
                        panelController = chain.thisObject
                        ModuleMain.instance.log("DeviceCardHook: captured MainPanelController")
                        return result
                    }
                })
        } catch (e: Exception) {
            ModuleMain.instance.log("DeviceCardHook: MainPanelController hook skipped — ${e.message}")
        }

        try {
            val deviceInfoWrapperClass = pluginClassLoader.loadClass(
                "miui.systemui.devicecenter.devices.DeviceInfoWrapper"
            )
            val performClickedMethod = deviceInfoWrapperClass.getDeclaredMethod(
                "performClicked", Context::class.java
            )
            ModuleMain.instance.hook(performClickedMethod)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        return interceptPerformClicked(chain) ?: chain.proceed()
                    }
                })
            ModuleMain.instance.log("DeviceCardHook: registered hook on DeviceInfoWrapper.performClicked")
        } catch (e: Exception) {
            ModuleMain.instance.log("DeviceCardHook: DeviceInfoWrapper hook skipped — ${e.message}")
        }
    }

    private fun interceptPerformClicked(chain: XposedInterface.Chain): Any? {
        try {
            val context = chain.args[0] as? Context ?: return null
            val instance = chain.thisObject ?: return null

            val deviceInfo = instance.javaClass.getDeclaredMethod("getDeviceInfo").invoke(instance)
                ?: return null
            val deviceType = deviceInfo.javaClass.getDeclaredMethod("getDeviceType")
                .invoke(deviceInfo) as? String ?: return null

            if (deviceType != "third_headset") return null

            val deviceId = deviceInfo.javaClass.getDeclaredMethod("getId")
                .invoke(deviceInfo) as? String ?: return null

            ModuleMain.instance.log("DeviceCardHook: third_headset clicked, id=$deviceId")

            val handlerThread = HandlerThread("openbuds_device_card_query").also { it.start() }
            val handler = Handler(handlerThread.looper)
            val latch = CountDownLatch(1)
            var localMac: String? = null

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(p0: Context?, intent: Intent?) {
                    if (intent?.action == CrossProcessActions.ACTION_DEVICE_MAC_RECEIVED) {
                        localMac = intent.getStringExtra(CrossProcessActions.EXTRA_DEVICE_MAC)
                        latch.countDown()
                    }
                }
            }

            val filter = IntentFilter(CrossProcessActions.ACTION_DEVICE_MAC_RECEIVED)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, null, handler, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter, null, handler)
            }

            try {
                Intent(CrossProcessActions.ACTION_QUERY_DEVICE_MAC).apply {
                    setPackage("dev.ignotus.openbuds")
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    context.sendBroadcast(this)
                }

                val received = latch.await(200, TimeUnit.MILLISECONDS)
                if (received && localMac != null && localMac == deviceId) {
                    ModuleMain.instance.log("DeviceCardHook: MAC match, launching QuickPopup")
                    context.startActivity(
                        Intent(context, QuickPopupActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                    MainPanelControllerProxy.exitOrHide(panelController)
                    localMac = null
                    context.unregisterReceiver(receiver)
                    handlerThread.quitSafely()
                    return Any() // non-null return skips chain.proceed(), cancelling original click
                }
            } catch (_: Exception) {
                // Fall through — let original click through
            }

            localMac = null
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            handlerThread.quitSafely()
        } catch (e: Exception) {
            ModuleMain.instance.log("DeviceCardHook: intercept error — ${e.message}")
        }
        return null // null return = let chain.proceed() run the original
    }
}
