package dev.ignotus.openbuds.lsposed

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.ignotus.openbuds.data.SonyHeadphoneRepository
import dev.ignotus.openbuds.data.SonyHeadphoneUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Injects local first-party-style content into the MLCard popup for Sony headphones.
 *
 * Hooks the MLCard window creation in com.milink.service, detects Sony devices,
 * establishes a BLE connection via the existing SonyHeadphoneRepository, and
 * injects battery/NC/EQ Views into the card's overlay FrameLayout.
 */
class CardContentHook(private val classLoader: ClassLoader) {

    private var probed = false
    private var activeRepository: SonyHeadphoneRepository? = null
    private var injectedContainer: LinearLayout? = null
    private val handler = Handler(Looper.getMainLooper())

    fun probe() {
        if (probed) return
        probed = true
        try {
            val clazz = Class.forName("android.view.WindowManagerGlobal")
            clazz.getDeclaredMethod("addView", View::class.java, ViewGroup.LayoutParams::class.java)
            ProbeResultCache.markFound("WindowManagerGlobal.addView")
            log("found WindowManagerGlobal.addView")
        } catch (_: Exception) {
            ProbeResultCache.markNotFound("WindowManagerGlobal.addView")
            log("WindowManagerGlobal.addView not found")
        }
        ProbeResultCache.persistShared()
    }

    fun hook() {
        try {
            val wmClass = Class.forName("android.view.WindowManagerGlobal")
            // Try multiple method signatures for different Android versions
            val addViewMethod = try {
                wmClass.getDeclaredMethod("addView", View::class.java, ViewGroup.LayoutParams::class.java)
            } catch (_: Exception) {
                wmClass.declaredMethods.firstOrNull { m ->
                    m.name == "addView" && m.parameterTypes.any { ViewGroup.LayoutParams::class.java.isAssignableFrom(it) }
                } ?: throw NoSuchMethodException("addView not found")
            }

            ModuleMain.instance.hook(addViewMethod)
                .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(object : io.github.libxposed.api.XposedInterface.Hooker {
                    override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
                        val result = chain.proceed()
                        val view = chain.args[0] as? View ?: return result
                        val lp = chain.args.getOrNull(1) as? ViewGroup.LayoutParams ?: return result
                        if (lp is WindowManager.LayoutParams &&
                            lp.title == "com.milink.card.frame.library.host.MLCard") {
                            log("MLCard window detected")
                            handler.postDelayed({ onCardWindowAdded(view as? ViewGroup) }, 200)
                        }
                        return result
                    }
                })
            log("hooked WindowManagerGlobal.addView")
        } catch (e: Exception) {
            log("FAILED to hook WindowManager: ${e.message}")
        }
    }

    // ── Card window detected ───────────────────────────────

    private fun onCardWindowAdded(rootView: ViewGroup?) {
        if (rootView == null) return

        // Find the device name TextView and overlay FrameLayout
        val finder = ViewFinder()
        rootView.findViewsWithText(
            ArrayList<View>().also { rootView.findViewsWithText(it, "查找超时", View.FIND_VIEWS_WITH_TEXT) },
            "查找超时", View.FIND_VIEWS_WITH_TEXT
        )

        // Traverse manually for the overlay FrameLayout (Loading/Error state container)
        val overlay = findOverlayFrame(rootView) ?: run {
            log("overlay FrameLayout not found in card view")
            return
        }
        val deviceNameView = findDeviceNameText(overlay)
        val deviceName = deviceNameView?.text?.toString() ?: return

        if (!MiLinkIdentityHook.matchesSonyPattern(deviceName)) return
        log("Sony card detected: \"$deviceName\"")

        val mac = MiLinkIdentityHook.lastSonyMac
        if (mac == null) {
            log("no cached MAC, cannot connect")
            return
        }

        // Get Context from the view (com.milink.service has Bluetooth permissions)
        val context = rootView.context.applicationContext

        // Inject content container
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            setBackgroundColor(Color.argb(180, 10, 10, 30))
            visibility = View.GONE // hidden until we have data
            addView(TextView(context).apply {
                textSize = 12f
                setTextColor(Color.parseColor("#888888"))
                typeface = Typeface.MONOSPACE
                text = "Connecting via BLE..."
                id = View.generateViewId()
                tag = "card_status"
            })
        }
        injectedContainer = container
        overlay.addView(container)

        // Start BLE connection via existing protocol stack
        val repo = SonyHeadphoneRepository.getInstance(context)
        activeRepository = repo
        repo.connect(mac, deviceName)
        log("BLE connecting to $mac")

        GlobalScope.launch(Dispatchers.Main) {
            repo.state.collectLatest { state ->
                updateCardContent(state, container, context)
            }
        }
    }

    // ── Content update ─────────────────────────────────────

    private fun updateCardContent(state: SonyHeadphoneUiState, container: LinearLayout, context: Context) {
        if (injectedContainer !== container) return

        container.removeAllViews()
        container.visibility = View.VISIBLE

        // Device name header
        val header = TextView(context).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            text = state.connectedDevice?.name ?: state.deviceInfo.modelName ?: "Sony Headphones"
            setPadding(0, 0, 0, 12)
        }
        container.addView(header)

        // Connection / protocol status
        if (!state.deviceInfo.protocolReady) {
            container.addView(statusLine(context, "Protocol: initializing..."))
            return
        }

        // Battery row
        val bat = buildString {
            state.batteryState.single?.let { append("$it%  ") }
            state.batteryState.left?.let { append("L:${it}%  ") }
            state.batteryState.right?.let { append("R:${it}%  ") }
            state.batteryState.cradle?.let { append("Case:${it}%") }
        }
        if (bat.isNotBlank()) {
            container.addView(infoLine(context, "Battery", bat.trim()))
        }

        // Noise control
        state.noiseControlState.controlMode?.let {
            container.addView(infoLine(context, "Noise Control", it.name))
        }

        // EQ
        state.eqState.preset?.let {
            container.addView(infoLine(context, "EQ", it.name))
        }

        // Wearing state
        state.wearingState.status?.let {
            container.addView(infoLine(context, "Wearing", it))
        }
    }

    private fun infoLine(context: Context, label: String, value: String): TextView {
        return TextView(context).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#CCCCCC"))
            text = "$label: $value"
            setPadding(0, 4, 0, 4)
        }
    }

    private fun statusLine(context: Context, text: String): TextView {
        return TextView(context).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#AAAAAA"))
            this.text = text
            setPadding(0, 4, 0, 4)
        }
    }

    // ── View traversal helpers ──────────────────────────────

    private fun findOverlayFrame(root: ViewGroup): ViewGroup? {
        return findChildByType(root, FrameLayout::class.java) { child ->
            // The overlay has ImageView + TextView + ProgressBar children
            var hasImage = false
            var hasProgress = false
            var hasText = false
            if (child is ViewGroup) {
                for (i in 0 until child.childCount) {
                    when (child.getChildAt(i)) {
                        is android.widget.ImageView -> hasImage = true
                        is android.widget.ProgressBar -> hasProgress = true
                        is TextView -> hasText = true
                    }
                }
            }
            hasImage && hasProgress && hasText
        }
    }

    private fun findDeviceNameText(parent: ViewGroup): TextView? {
        val candidates = mutableListOf<TextView>()
        findViewsByType(parent, TextView::class.java, candidates)
        // Device name is the longest TextView in the overlay (not the error/toast text)
        return candidates.filter { it.text.length > 3 && it.text.length < 50 }
            .maxByOrNull { it.text.length }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : View> findChildByType(root: ViewGroup, type: Class<T>, predicate: (T) -> Boolean): T? {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (type.isInstance(child) && predicate(child as T)) return child
            if (child is ViewGroup) {
                val found = findChildByType(child, type, predicate)
                if (found != null) return found
            }
        }
        return null
    }

    private fun <T : View> findViewsByType(root: ViewGroup, type: Class<T>, out: MutableList<T>) {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (type.isInstance(child)) out.add(child as T)
            if (child is ViewGroup) findViewsByType(child, type, out)
        }
    }

    // ── ViewFinder helper class ─────────────────────────────

    private class ViewFinder : ArrayList<View>() {
        // Used with findViewsWithText
    }

    // ── Logging ─────────────────────────────────────────────

    companion object {
        fun log(msg: String) {
            android.util.Log.i("OpenBuds", "[CardContent] $msg")
        }
    }
}
