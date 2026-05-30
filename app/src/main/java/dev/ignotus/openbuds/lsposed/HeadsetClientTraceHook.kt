package dev.ignotus.openbuds.lsposed

import android.os.Message

/**
 * Phase 3 fix: hook HeadsetServiceClient.b(Message) to correct
 * HeadsetHost.name for Sony devices.
 */
class HeadsetClientTraceHook(private val classLoader: ClassLoader) {

    private var probed = false
    private var hsClientClass: Class<*>? = null
    private var headsetHostClass: Class<*>? = null
    private var nameField: java.lang.reflect.Field? = null

    fun probe(): Boolean {
        if (probed) return hsClientClass != null
        probed = true

        hsClientClass = try {
            classLoader.loadClass("com.miui.circulate.api.protocol.headset.HeadsetServiceClient")
        } catch (_: Exception) { null }

        // Try multiple possible class names for HeadsetHost
        for (candidate in listOf(
            "com.miui.circulate.api.protocol.headset.HeadsetHost",
            "com.miui.headset.HeadsetHost",
            "com.miui.circulate.HeadsetHost",
        )) {
            try {
                headsetHostClass = classLoader.loadClass(candidate)
                // Find a String field that might be 'name'
                for (f in headsetHostClass!!.declaredFields) {
                    log("HeadsetHost field: ${f.type.simpleName} ${f.name}")
                }
                nameField = headsetHostClass!!.getDeclaredField("name").also { it.isAccessible = true }
                log("HeadsetHost.name found in $candidate")
                break
            } catch (_: Exception) {}
        }
        if (headsetHostClass == null) {
            log("HeadsetHost class NOT FOUND — will try runtime detection")
        }

        return hsClientClass != null
    }

    fun hook() {
        val clazz = hsClientClass ?: return

        try {
            val method = clazz.getDeclaredMethod("b", clazz, Message::class.java)
            ModuleMain.instance.hook(method)
                .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(object : io.github.libxposed.api.XposedInterface.Hooker {
                    override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
                        val msg = chain.args.getOrNull(1) as? Message
                        if (msg != null && msg.what == 0) {
                            handleMessage(msg)
                        }
                        return chain.proceed()
                    }
                })
            log("hooked b(Message)")
        } catch (e: Exception) {
            log("FAILED: ${e.message}")
        }
    }

    private fun handleMessage(msg: Message) {
        val obj = msg.obj ?: return
        val cls = obj.javaClass

        // First time: log the actual class and its fields
        if (headsetHostClass == null) {
            log("msg.obj class: ${cls.name} (simple: ${cls.simpleName})")
            for (f in cls.declaredFields) {
                f.isAccessible = true
                try {
                    val value = f.get(obj)
                    log("  field: ${f.type.simpleName} ${f.name} = ${value?.toString()?.take(80)}")
                } catch (_: Exception) {
                    log("  field: ${f.type.simpleName} ${f.name} = <error>")
                }
            }
            // Try to find and cache the name field
            for (f in cls.declaredFields) {
                if (f.type == String::class.java && f.name.lowercase().contains("name")) {
                    f.isAccessible = true
                    nameField = f
                    log("cached name field: ${f.name}")
                    break
                }
            }
            if (nameField == null) {
                // If no explicit name field, try any String field
                for (f in cls.declaredFields) {
                    if (f.type == String::class.java) {
                        f.isAccessible = true
                        nameField = f
                        log("cached String field as name: ${f.name}")
                        break
                    }
                }
            }
            headsetHostClass = cls // cache for next time
        }

        // Fix the name
        try {
            val currentName = nameField?.get(obj) as? String ?: return
            if (!currentName.contains("的Xiaomi")) return
            val correctName = MiLinkIdentityHook.lastSonyName ?: return
            if (correctName.isEmpty()) return
            nameField?.set(obj, correctName)
            log("Fixed: \"${currentName.take(30)}\" → \"$correctName\"")
        } catch (e: Exception) {
            log("fix error: ${e.message}")
        }
    }

    companion object {
        fun log(msg: String) {
            android.util.Log.i("OpenBuds", "[MSG] $msg")
        }
    }
}
