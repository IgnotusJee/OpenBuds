package dev.ignotus.openbuds.lsposed

import android.os.Message
import android.widget.TextView

/**
 * Hook android.widget.TextView.setText(CharSequence) to intercept
 * wrong device name display for Sony devices in MLCard.
 *
 * This is framework-level (android.widget.TextView), not MiLink-specific.
 * Only replaces text matching the account-name pattern "的Xiaomi".
 */
class TextViewNameFixHook(private val classLoader: ClassLoader) {

    private var hooked = false

    fun hook() {
        if (hooked) return
        hooked = true

        try {
            val tvClass = TextView::class.java
            val setTextMethod = tvClass.getDeclaredMethod("setText", CharSequence::class.java)

            ModuleMain.instance.hook(setTextMethod)
                .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(object : io.github.libxposed.api.XposedInterface.Hooker {
                    override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
                        val text = chain.args[0] as? CharSequence
                        if (text != null && text.contains("的Xiaomi")) {
                            val correct = MiLinkIdentityHook.lastSonyName
                            if (correct != null && correct.isNotEmpty()) {
                                chain.args[0] = correct
                            }
                        }
                        return chain.proceed()
                    }
                })
            log("hooked TextView.setText")
        } catch (e: Exception) {
            log("FAILED: ${e.message}")
        }
    }

    companion object {
        fun log(msg: String) { android.util.Log.i("OpenBuds", "[TVFix] $msg") }
    }
}
