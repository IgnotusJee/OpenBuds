package dev.ignotus.openbuds.integration.milink

/**
 * UI-facing adapter status snapshot.
 *
 * Mirrors the [MilinkBridgeContract] status bundle, providing a typed view
 * of whether the MiLink adapter is active, the bridge is connected, and
 * which devices are currently authorized.
 */
data class MilinkAdapterStatus(
    val enabled: Boolean,
    val connected: Boolean,
    val protocolReady: Boolean,
    val reason: String,
    val authorizedMacs: List<String>,
    val lastError: String?,
)
