package dev.ignotus.openbuds.lsposed

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.ignotus.openbuds.data.SonyHeadphoneRepository
import dev.ignotus.openbuds.data.SonyHeadphoneUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Collections

class CardContentHook(private val classLoader: ClassLoader) {

    private var probed = false
    private var activeRepository: SonyHeadphoneRepository? = null
    private var injectedContainer: LinearLayout? = null
    private val handler = Handler(Looper.getMainLooper())
    private var hookAttempted = false

    // Phase 4: native MLCard view population
    val mlCardHook = MLCardNativeControlsHook()

    // Dedup: track already-processed views (identityHashCode)
    private val processedCards = Collections.newSetFromMap(java.util.WeakHashMap<View, Boolean>())

    private val cardTitlePatterns = listOf(
        "com.milink.card.frame.library.host.MLCard",
        "MLCard",
        "CirculateCard",
    )

    // UI words to exclude from device name detection
    private val uiWords = setOf("断开", "更多设置", "disconnect", "已连接", "未连接", "连接", "设置")

    fun probe() {
        if (probed) return
        probed = true
        ProbeResultCache.persistShared()
    }

    fun hook() {
        if (hookAttempted) return
        hookAttempted = true

        // Hook WM addView
        var count = 0
        for (wmClassName in listOf("android.view.WindowManagerGlobal", "android.view.WindowManagerImpl")) {
            try {
                val wmClass = Class.forName(wmClassName)
                for (m in wmClass.declaredMethods) {
                    if (m.name == "addView" && m.parameterTypes.size >= 2 &&
                        View::class.java.isAssignableFrom(m.parameterTypes[0]) &&
                        ViewGroup.LayoutParams::class.java.isAssignableFrom(m.parameterTypes[1])) {
                        hookAddViewMethod(m, wmClassName)
                        count++
                    }
                }
            } catch (_: Exception) {}
        }
        log("hooked $count addView methods")

        // Also hook Dialog/PopupWindow for control center path
        hookDialogShow()
    }

    private fun hookDialogShow() {
        try {
            val dialogClass = Class.forName("android.app.Dialog")
            val showMethod = dialogClass.getDeclaredMethod("show")
            ModuleMain.instance.hook(showMethod)
                .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(object : io.github.libxposed.api.XposedInterface.Hooker {
                    override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
                        val result = chain.proceed()
                        val dialog = chain.thisObject
                        val window = dialog.javaClass.getMethod("getWindow").invoke(dialog) as? android.view.Window
                        val decorView = window?.decorView as? ViewGroup ?: return result
                        handler.postDelayed({ checkForCardView(decorView, "Dialog") }, 200)
                        return result
                    }
                })
            log("hooked Dialog.show")
        } catch (e: Exception) {
            log("Dialog.show hook failed: ${e.message}")
        }
    }

    // ── Window addView hook ────────────────────────────────

    private fun hookAddViewMethod(method: java.lang.reflect.Method, className: String) {
        ModuleMain.instance.hook(method)
            .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : io.github.libxposed.api.XposedInterface.Hooker {
                override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val view = chain.args[0] as? View ?: return result
                    val lp = chain.args.getOrNull(1) as? ViewGroup.LayoutParams ?: return result
                    if (lp is WindowManager.LayoutParams) {
                        val title = lp.title?.toString() ?: ""
                        if (title.isNotEmpty()) {
                            log("WM title=\"$title\"")
                        }
                        if (cardTitlePatterns.any { title.contains(it, ignoreCase = true) }) {
                            log("!!! MLCard $className")
                            handler.postDelayed({ checkForCardView(view, className) }, 300)
                        }
                    }
                    return result
                }
            })
    }

    // ── Card view check (deduped) ──────────────────────────

    private fun checkForCardView(view: View, source: String) {
        val root = view as? ViewGroup ?: return
        if (!processedCards.add(view)) return  // dedup

        log("checking card from $source: ${root.javaClass.simpleName}[${root.childCount}]")
        dumpViewTree(root, 0, 5)

        val deviceName = findDeviceNameInCard(root) ?: run {
            log("no device name found in card")
            return
        }
        log("device name: \"$deviceName\"")

        if (!MiLinkIdentityHook.matchesSonyPattern(deviceName)) {
            log("not Sony → skip")
            return
        }
        log("Sony confirmed: \"$deviceName\"")

        val mac = MiLinkIdentityHook.lastSonyMac
        if (mac == null) {
            log("no cached MAC")
            return
        }

        val context = root.context.applicationContext
        val repo = SonyHeadphoneRepository.getInstance(context)
        activeRepository = repo
        repo.connect(mac, deviceName)
        log("repo.connect($mac)")

        // Phase 4: try to populate native MLCard views first
        if (mlCardHook.populateCard(root, repo, deviceName)) {
            log("Native MLCard views populated — skipping custom injection")
            return
        }
        log("Native views not found — falling back to custom LinearLayout injection")

        // Inject into the card's content area — find MainCardView or RelativeLayout
        val injectTarget = findCardContentArea(root) ?: root
        log("injecting into ${injectTarget.javaClass.simpleName}")
        val container = LinearLayout(context).apply {
            id = View.generateViewId()
            tag = "openbuds_card_content"
            orientation = LinearLayout.VERTICAL
            setPadding(32, 20, 32, 20)
            addView(TextView(context).apply {
                textSize = 12f
                setTextColor(Color.parseColor("#888888"))
                typeface = Typeface.MONOSPACE
                text = "Connecting BLE..."
                id = View.generateViewId()
                tag = "openbuds_card_status"
            })
        }
        injectedContainer = container
        injectTarget.addView(container)

        // Fix display name if wrong (from control center)
        // Note: device name is now fixed at source via HeadsetClientTraceHook
        fixDeviceNameDisplay(root, deviceName)

        GlobalScope.launch(Dispatchers.Main) {
            repo.state.collectLatest { state ->
                updateCardContent(state, container, context)
            }
        }
    }

    private fun findCardContentArea(root: ViewGroup): ViewGroup? {
        // Find MainCardView or a child RelativeLayout/LinearLayout inside it
        val mainCard = findChildByType(root, ViewGroup::class.java) { v ->
            v.javaClass.simpleName.contains("MainCard", ignoreCase = true)
        }
        if (mainCard != null && mainCard.childCount > 0) {
            // First child is usually the content layout
            return mainCard.getChildAt(0) as? ViewGroup
        }
        return null
    }

    private fun fixDeviceNameDisplay(root: ViewGroup, correctName: String) {
        val candidates = mutableListOf<TextView>()
        collectAllTextViews(root, candidates)
        for (tv in candidates) {
            if (tv.tag == "openbuds_card_status") continue  // skip our views
            val text = tv.text?.toString() ?: ""
            if (text.contains("的Xiaomi") || text.contains("Xiaomi") && text.length > 30) {
                tv.text = correctName
                log("fixed name: \"$text\" → \"$correctName\"")
            }
        }
    }

    private fun findDeviceNameInCard(root: ViewGroup): String? {
        val candidates = mutableListOf<TextView>()
        collectAllTextViews(root, candidates)
        log("${candidates.size} TextViews")
        for (tv in candidates.take(10)) {
            log("  TV: \"${tv.text}\" (len=${tv.text.length} tag=${tv.tag})")
        }
        // Filter: skip our injected views, UI words, account-name patterns
        val valid = candidates.filter { tv ->
            if (tv.tag == "openbuds_card_status") return@filter false
            val t = tv.text.toString().trim()
            if (t.isEmpty() || t.length !in 3..50) return@filter false
            if (uiWords.any { t == it || t.contains(it) }) return@filter false
            // Skip account-name format: "XXX的Xiaomi XXX" from control center
            if (t.contains("的Xiaomi") && t.length > 10) return@filter false
            true
        }
        return valid.maxByOrNull { it.text.length }?.text?.toString()
            ?: MiLinkIdentityHook.lastSonyName  // fallback to cached name
    }

    // ── Content update ─────────────────────────────────────

    private fun updateCardContent(state: SonyHeadphoneUiState, container: LinearLayout, context: Context) {
        if (injectedContainer !== container) return
        container.removeAllViews()

        val header = TextView(context).apply {
            textSize = 16f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            text = state.connectedDevice?.name ?: state.deviceInfo.modelName ?: "Sony Headphones"
            setPadding(0, 0, 0, 12)
        }
        container.addView(header)

        if (!state.deviceInfo.protocolReady) {
            container.addView(statusLine(context, "Protocol initializing..."))
            return
        }

        val bat = buildString {
            state.batteryState.single?.let { append("$it%  ") }
            state.batteryState.left?.let { append("L:${it}%  ") }
            state.batteryState.right?.let { append("R:${it}%  ") }
            state.batteryState.cradle?.let { append("Case:${it}%") }
        }
        if (bat.isNotBlank()) container.addView(infoLine(context, "Battery", bat.trim()))
        state.noiseControlState.controlMode?.let { container.addView(infoLine(context, "Noise Control", it.name)) }
        state.eqState.preset?.let { container.addView(infoLine(context, "EQ", it.name)) }
        state.wearingState.status?.let { container.addView(infoLine(context, "Wearing", it)) }
    }

    private fun infoLine(context: Context, label: String, value: String): TextView =
        TextView(context).apply {
            textSize = 13f; setTextColor(Color.parseColor("#CCCCCC"))
            text = "$label: $value"; setPadding(0, 4, 0, 4)
        }

    private fun statusLine(context: Context, text: String): TextView =
        TextView(context).apply {
            textSize = 12f; setTextColor(Color.parseColor("#AAAAAA"))
            this.text = text; setPadding(0, 4, 0, 4)
        }

    // ── View traversal ──────────────────────────────────────

    private fun dumpViewTree(view: View, depth: Int, maxDepth: Int) {
        if (depth > maxDepth) return
        val indent = "  ".repeat(depth)
        val idStr = if (view.id != View.NO_ID) " #${Integer.toHexString(view.id)}" else ""
        val desc = when (view) {
            is ViewGroup -> "${view.javaClass.simpleName}[${view.childCount}]$idStr"
            is TextView -> "${view.javaClass.simpleName}$idStr \"${view.text}\""
            else -> "${view.javaClass.simpleName}$idStr"
        }
        log("$indent$desc")
        if (view is ViewGroup && depth < maxDepth) {
            for (i in 0 until view.childCount) {
                dumpViewTree(view.getChildAt(i), depth + 1, maxDepth)
            }
        }
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

    private fun collectAllTextViews(root: ViewGroup, out: MutableList<TextView>) {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is TextView) out.add(child)
            if (child is ViewGroup) collectAllTextViews(child, out)
        }
    }

    companion object {
        fun log(msg: String) {
            android.util.Log.i("OpenBuds", "[CC] $msg")
        }
    }
}
