package dev.ignotus.openbuds.lsposed

import android.util.Log

object MainPanelControllerProxy {
    private const val TAG = "OpenBuds"

    fun exitOrHide(controller: Any?) {
        if (controller == null) return
        try {
            val method = controller.javaClass.getDeclaredMethod("exitOrHide")
            method.isAccessible = true
            method.invoke(controller)
        } catch (e: Exception) {
            Log.e(TAG, "MainPanelControllerProxy.exitOrHide failed", e)
        }
    }
}
