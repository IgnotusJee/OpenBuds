package dev.ignotus.openbuds.lsposed

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ProgressBar
import android.widget.TextView
import dev.ignotus.openbuds.data.SonyHeadphoneRepository
import dev.ignotus.openbuds.data.SonyHeadphoneUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Collections

/**
 * Phase 4: Make MLCard render first-party controls with real BLE data.
 *
 * Strategy: traverse the MLCard view hierarchy at runtime, identify child views
 * that display headphone data (battery, ANC, device name, etc.), and directly
 * update them with values from SonyHeadphoneRepository.state.
 *
 * This works without knowing the exact data class structure (headsetInfo, HeadsetHost)
 * because it manipulates the rendered Views rather than the data pipeline.
 *
 * Each identified view is tagged with "openbuds_mapped" to avoid re-identification.
 */
class MLCardNativeControlsHook {

    // Track cards already processed (identityHashCode)
    private val processedCards = Collections.newSetFromMap(java.util.WeakHashMap<View, Boolean>())

    // Active data collection coroutine (one per card)
    private var activeRepo: SonyHeadphoneRepository? = null

    // ── View identification ──────────────────────────────────

    /**
     * View role descriptors found in the card hierarchy.
     * Each identifies a View and how to update it.
     */
    private enum class ViewRole {
        DEVICE_NAME,          // TextView showing the headphone name
        BATTERY_LEFT,         // TextView or ProgressBar for left ear battery
        BATTERY_RIGHT,        // TextView or ProgressBar for right ear battery
        BATTERY_CASE,         // TextView or ProgressBar for case battery
        BATTERY_SINGLE,       // TextView or ProgressBar for single battery (headset)
        ANC_MODE,             // TextView showing ANC mode text, or Switch/CheckBox
        ANC_TOGGLE,           // CompoundButton (Switch/CheckBox) for ANC on/off
        WEARING_STATUS,       // TextView showing wearing detection
        EQ_PRESET,            // TextView showing EQ preset name
        FIRMWARE_VERSION,     // TextView showing firmware version
        CONNECTION_STATUS,    // TextView showing connection state
    }

    private data class MappedView(
        val role: ViewRole,
        val view: View,
        val originalText: String? = null,
    )

    // ── Entry point ──────────────────────────────────────────

    /**
     * Attempt to populate native MLCard views with data from [repo].
     * Called from CardContentHook when a Sony device is confirmed.
     *
     * @return true if native views were found and populated; false if fallback injection should be used.
     */
    fun populateCard(cardView: View, repo: SonyHeadphoneRepository, deviceName: String): Boolean {
        if (!processedCards.add(cardView)) return true // already processing
        if (cardView !is ViewGroup) return false

        log("populateCard start: ${cardView.javaClass.simpleName}[${cardView.childCount}]")

        // Phase A: Discover the MLCard's view hierarchy (card-local list)
        val mappedViews = mutableListOf<MappedView>()
        discoverViews(cardView, mappedViews, deviceName)

        val hasOnlyName = mappedViews.all { it.role == ViewRole.DEVICE_NAME }
        val tooFewViews = mappedViews.size < 2

        if (hasOnlyName || tooFewViews) {
            log("Only ${mappedViews.size} views found (device name only) — scheduling retries")
            // Schedule retries: views may be added asynchronously by MiLink
            val handler = Handler(Looper.getMainLooper())
            val retryDelays = longArrayOf(500, 1200, 2500)
            scheduleRetry(cardView, repo, deviceName, mappedViews, handler, retryDelays, 0)
            return false // let CardContentHook fallback run in the meantime
        }

        if (mappedViews.isEmpty()) {
            log("No mappable native views found — fall back to custom injection")
            processedCards.remove(cardView)
            return false
        }

        // Phase B: Start data feed
        startDataFeed(cardView, repo, mappedViews, deviceName)
        return true
    }

    private fun scheduleRetry(
        cardView: ViewGroup,
        repo: SonyHeadphoneRepository,
        deviceName: String,
        mappedViews: MutableList<MappedView>,
        handler: Handler,
        delays: LongArray,
        attempt: Int,
    ) {
        if (attempt >= delays.size) {
            log("Retries exhausted — card still has only ${mappedViews.size} views")
            return
        }
        if (!cardView.isAttachedToWindow) {
            log("Card detached during retry — aborting")
            return
        }
        handler.postDelayed({
            if (!cardView.isAttachedToWindow) {
                log("Card detached before retry $attempt — aborting")
                return@postDelayed
            }
            log("Retry $attempt: re-scanning view tree...")
            val newCount = mappedViews.size
            discoverViews(cardView, mappedViews, deviceName)
            val added = mappedViews.size - newCount
            log("Retry $attempt: found $added new views (total=${mappedViews.size})")
            for (mv in mappedViews.drop(newCount)) {
                log("  + ${mv.role}: ${mv.view.javaClass.simpleName} text=\"${mv.originalText}\"")
            }
            if (mappedViews.size > newCount) {
                // New views found — apply latest BLE state
                val state = repo.state.value
                updateMappedViews(mappedViews, state, deviceName)
            }
            if (mappedViews.all { it.role == ViewRole.DEVICE_NAME }) {
                scheduleRetry(cardView, repo, deviceName, mappedViews, handler, delays, attempt + 1)
            }
        }, delays[attempt])
    }

    private fun startDataFeed(
        cardView: View,
        repo: SonyHeadphoneRepository,
        mappedViews: MutableList<MappedView>,
        deviceName: String,
    ) {
        activeRepo = repo

        log("Found ${mappedViews.size} mappable views: ${mappedViews.map { it.role }.distinct()}")
        for (mv in mappedViews) {
            log("  ${mv.role}: ${mv.view.javaClass.simpleName} tag=${mv.view.tag} text=\"${mv.originalText}\"")
        }

        GlobalScope.launch(Dispatchers.Main) {
            repo.state.collectLatest { state ->
                updateMappedViews(mappedViews, state, deviceName)
            }
        }

        // Clean up when card is detached
        cardView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                log("MLCard detached — cleanup")
                mappedViews.clear()
                activeRepo = null
                processedCards.remove(cardView)
            }
        })
    }

    // ── View discovery ───────────────────────────────────────

    private fun discoverViews(root: ViewGroup, mappedViews: MutableList<MappedView>, deviceName: String) {
        val allTextViews = mutableListOf<TextView>()
        val allProgressBars = mutableListOf<ProgressBar>()
        val allSwitches = mutableListOf<CompoundButton>()
        collectViews(root, allTextViews, allProgressBars, allSwitches)

        log("Discovered: ${allTextViews.size} TVs, ${allProgressBars.size} PBs, ${allSwitches.size} switches")

        // Identify TextViews by their content and position
        for (tv in allTextViews) {
            if (tv.tag == "openbuds_mapped") continue
            val text = tv.text?.toString()?.trim() ?: ""

            when {
                // Device name: matches original device name or contains the stored name
                text.equals(deviceName, ignoreCase = true) ||
                    text.contains("WF-", ignoreCase = true) ||
                    text.contains("WH-", ignoreCase = true) ||
                    text.contains("LinkBuds", ignoreCase = true) ||
                    text.contains("Xiaomi", ignoreCase = true) -> {
                    mapView(mappedViews, ViewRole.DEVICE_NAME, tv)
                }

                // Battery percentage: single/double digit number, possibly with "%"
                text.matches(Regex("""^(\d{1,3})%?$""")) -> {
                    // Heuristic: determine which battery this is by position/index
                    val idx = allTextViews.indexOf(tv)
                    val role = when {
                        text == "100" || text == "100%" -> {
                            // 100 could be anything; check context
                            when {
                                idx > 0 && allTextViews.getOrNull(idx - 1)?.text?.contains("L") == true -> ViewRole.BATTERY_LEFT
                                idx > 0 && allTextViews.getOrNull(idx - 1)?.text?.contains("R") == true -> ViewRole.BATTERY_RIGHT
                                idx > 0 && allTextViews.getOrNull(idx - 1)?.text?.contains("Case") == true -> ViewRole.BATTERY_CASE
                                else -> null
                            }
                        }
                        // Used in context of nearby text
                        else -> assignBatteryRoleFromContext(tv, allTextViews)
                    }
                    if (role != null) mapView(mappedViews, role, tv)
                }

                // ANC mode text
                text.contains("降噪", ignoreCase = true) ||
                    text.contains("Noise", ignoreCase = true) ||
                    text.contains("ANC", ignoreCase = true) ||
                    text.contains("Ambient", ignoreCase = true) ||
                    text.contains("透明", ignoreCase = true) ||
                    text.contains("环境", ignoreCase = true) ||
                    text.contains("通透", ignoreCase = true) -> {
                    mapView(mappedViews, ViewRole.ANC_MODE, tv)
                }

                // Wearing / ear detection
                text.contains("佩戴", ignoreCase = true) ||
                    text.contains("wear", ignoreCase = true) ||
                    text.contains("入耳", ignoreCase = true) ||
                    text.contains("ear", ignoreCase = true) ||
                    text.contains("in-ear", ignoreCase = true) -> {
                    mapView(mappedViews, ViewRole.WEARING_STATUS, tv)
                }

                // EQ preset
                text.contains("EQ", ignoreCase = true) ||
                    text.contains("均衡", ignoreCase = true) ||
                    text.contains("Bass", ignoreCase = true) ||
                    text.contains("Bright", ignoreCase = true) ||
                    text.contains("Custom", ignoreCase = true) -> {
                    mapView(mappedViews, ViewRole.EQ_PRESET, tv)
                }

                // Firmware version
                text.matches(Regex("""\d+\.\d+\.\d+""")) ||
                    text.contains("FW", ignoreCase = true) ||
                    text.contains("固件", ignoreCase = true) -> {
                    mapView(mappedViews, ViewRole.FIRMWARE_VERSION, tv)
                }

                // Connection status
                (text.contains("已连接", ignoreCase = true) ||
                    text.contains("Connected", ignoreCase = true) ||
                    text.contains("已断开", ignoreCase = true) ||
                    text.contains("Disconnected", ignoreCase = true)) -> {
                    mapView(mappedViews, ViewRole.CONNECTION_STATUS, tv)
                }
            }
        }

        // Identify ProgressBars as battery indicators
        for ((i, pb) in allProgressBars.withIndex()) {
            if (pb.tag == "openbuds_mapped") continue
            // Multiple progress bars → left/right/case batteries
            val role = when {
                allProgressBars.size >= 3 && i == 0 -> ViewRole.BATTERY_LEFT
                allProgressBars.size >= 3 && i == 1 -> ViewRole.BATTERY_RIGHT
                allProgressBars.size >= 3 && i == 2 -> ViewRole.BATTERY_CASE
                allProgressBars.size == 1 -> ViewRole.BATTERY_SINGLE
                else -> null
            }
            if (role != null) mapView(mappedViews, role, pb)
        }

        // Identify Switches as ANC toggle
        for (sw in allSwitches) {
            if (sw.tag == "openbuds_mapped") continue
            mapView(mappedViews, ViewRole.ANC_TOGGLE, sw)
        }
    }

    private fun assignBatteryRoleFromContext(tv: TextView, allTvs: List<TextView>): ViewRole? {
        val idx = allTvs.indexOf(tv)
        // Look at nearby TextViews for L/R/Case labels
        for (offset in -2..2) {
            if (offset == 0) continue
            val neighbor = allTvs.getOrNull(idx + offset) ?: continue
            val nText = neighbor.text?.toString()?.trim() ?: ""
            when {
                nText.contains("L") || nText.contains("左") -> return ViewRole.BATTERY_LEFT
                nText.contains("R") || nText.contains("右") -> return ViewRole.BATTERY_RIGHT
                nText.contains("Case") || nText.contains("盒") || nText.contains("仓") -> return ViewRole.BATTERY_CASE
            }
        }
        return null
    }

    private fun mapView(mappedViews: MutableList<MappedView>, role: ViewRole, view: View) {
        view.tag = "openbuds_mapped"
        val originalText = (view as? TextView)?.text?.toString()
        mappedViews.add(MappedView(role, view, originalText))
    }

    private fun collectViews(
        root: ViewGroup,
        tvs: MutableList<TextView>,
        pbs: MutableList<ProgressBar>,
        switches: MutableList<CompoundButton>,
    ) {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            when (child) {
                is TextView -> tvs.add(child)
                is ProgressBar -> pbs.add(child)
                is CompoundButton -> switches.add(child)
                is ViewGroup -> collectViews(child, tvs, pbs, switches)
            }
        }
    }

    // ── View update ──────────────────────────────────────────

    private fun updateMappedViews(mappedViews: List<MappedView>, state: SonyHeadphoneUiState, deviceName: String) {
        for (mv in mappedViews) {
            try {
                when (mv.role) {
                    ViewRole.DEVICE_NAME -> {
                        (mv.view as? TextView)?.text = state.connectedDevice?.name
                            ?: state.deviceInfo.modelName ?: deviceName
                    }
                    ViewRole.BATTERY_LEFT -> updateBatteryView(mv.view, state.batteryState.left)
                    ViewRole.BATTERY_RIGHT -> updateBatteryView(mv.view, state.batteryState.right)
                    ViewRole.BATTERY_CASE -> updateBatteryView(mv.view, state.batteryState.cradle)
                    ViewRole.BATTERY_SINGLE -> updateBatteryView(mv.view, state.batteryState.single)
                    ViewRole.ANC_MODE -> {
                        val mode = state.noiseControlState.controlMode?.name ?: "Unknown"
                        (mv.view as? TextView)?.text = mode
                    }
                    ViewRole.ANC_TOGGLE -> {
                        val isOn = state.noiseControlState.noiseCancellingEnabled == true
                        (mv.view as? CompoundButton)?.isChecked = isOn
                    }
                    ViewRole.WEARING_STATUS -> {
                        (mv.view as? TextView)?.text = state.wearingState.status ?: "Unknown"
                    }
                    ViewRole.EQ_PRESET -> {
                        (mv.view as? TextView)?.text = state.eqState.preset?.name ?: "Off"
                    }
                    ViewRole.FIRMWARE_VERSION -> {
                        (mv.view as? TextView)?.text = state.deviceInfo.firmwareVersion ?: "—"
                    }
                    ViewRole.CONNECTION_STATUS -> {
                        val status = if (state.connectedDevice != null) "已连接" else "未连接"
                        (mv.view as? TextView)?.text = status
                    }
                }
            } catch (_: Exception) {
                // View may have been removed from hierarchy
            }
        }
    }

    private fun updateBatteryView(view: View, level: Int?) {
        when (view) {
            is TextView -> {
                view.text = if (level != null) "$level%" else "—"
            }
            is ProgressBar -> {
                if (level != null) {
                    view.max = 100
                    view.progress = level
                } else {
                    view.progress = 0
                }
            }
        }
    }

    // ── Cleanup ──────────────────────────────────────────────

    fun cleanup() {
        activeRepo = null
    }

    companion object {
        fun log(msg: String) {
            android.util.Log.i("OpenBuds", "[MC] $msg")
        }
    }
}
