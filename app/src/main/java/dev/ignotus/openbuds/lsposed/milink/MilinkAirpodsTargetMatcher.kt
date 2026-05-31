package dev.ignotus.openbuds.lsposed.milink

/**
 * MAC allowlist matcher for the AirPods adapter path.
 *
 * Determines whether a Bluetooth device MAC should be treated as a
 * "first-party" (AirPods-emulated) device by milink's rendering pipeline.
 *
 * ## Allowlist sources (priority order)
 *
 * 1. System property [DEBUG_PROPERTY] — comma-separated MACs set via
 *    `adb shell setprop debug.openbuds.milink_m1_macs "XX:XX:XX:XX:XX:XX,..."`.
 *    Intended for M1–M2 debugging. Readable by milink process (system UID).
 * 2. [defaultTargetMacs] — hardcoded placeholder, replaced by real bridge
 *    authorization in M3.
 *
 * ## Decision logic
 *
 * [airpodsDecision] uses `originalResult || isTargetMac`:
 * - Real AirPods (originalResult = true) are always transparently passed through.
 * - OpenBuds devices return true only when their MAC is in the configured target set.
 *
 * @see MilinkAirpodsM1Hook
 */
object MilinkAirpodsTargetMatcher {
    /** System property key for comma-separated debug MAC allowlist. */
    const val DEBUG_PROPERTY = "debug.openbuds.milink_m1_macs"

    /** Standard colon-separated 6-octet MAC format. */
    private val macRegex = Regex("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$")

    /**
     * Hardcoded placeholder MACs used when no system property override is set.
     * Only matches the dummy MAC; real devices must set [DEBUG_PROPERTY].
     */
    private val defaultTargetMacs = setOf("00:11:22:33:44:55")

    /**
     * Normalizes a raw MAC string to uppercase colon-separated format.
     * Returns `null` if the string is blank or doesn't match [macRegex].
     */
    fun normalizeMac(value: String?): String? {
        val normalized = value
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return normalized.takeIf { macRegex.matches(it) }
    }

    /**
     * Splits a delimited MAC list string into a normalized set.
     * Accepts commas, semicolons, whitespace, and newlines as separators.
     */
    fun parseMacList(value: String?): Set<String> =
        value
            ?.split(',', ';', '\n', '\r', '\t', ' ')
            ?.mapNotNull { normalizeMac(it) }
            ?.toSet()
            .orEmpty()

    /**
     * Returns the effective target MAC set:
     * parsed property override if non-empty, otherwise [defaultTargetMacs].
     */
    fun configuredTargets(debugPropertyValue: String?): Set<String> =
        parseMacList(debugPropertyValue).ifEmpty { defaultTargetMacs }

    /**
     * Checks whether a given MAC address is in the configured target set.
     * Returns `false` for null or malformed MACs.
     */
    fun isTargetMac(mac: String?, debugPropertyValue: String?): Boolean {
        val normalized = normalizeMac(mac) ?: return false
        return normalized in configuredTargets(debugPropertyValue)
    }

    /**
     * The core decision: should milink treat this device as an AirPods?
     *
     * Returns `true` when:
     * - The original (unhooked) result was already `true` (genuine AirPods → transparent pass-through), OR
     * - The MAC matches our configured target set (OpenBuds device → we claim it as AirPods).
     */
    fun airpodsDecision(originalResult: Boolean, mac: String?, debugPropertyValue: String?): Boolean =
        originalResult || isTargetMac(mac, debugPropertyValue)
}
