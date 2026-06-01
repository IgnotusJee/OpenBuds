package dev.ignotus.openbuds.ble.sony

/**
 * Diagnostics for a GATT endpoint that does not expose a usable Tandem
 * control service.
 */
data class UnsupportedEndpointDiagnostics(
    val reason: String,
    val serviceLabels: List<String>,
    val leAudioSwitchCompatibility: Int? = null,
    val friendlyName: String? = null,
    val publicAddress: String? = null,
    val rawReads: Map<String, String> = emptyMap(),
)
