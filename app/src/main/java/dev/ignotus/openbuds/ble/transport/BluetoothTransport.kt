package dev.ignotus.openbuds.ble.transport

/**
 * Metadata describing the active transport connection.
 */
data class TransportInfo(
    val mtu: Int,
    val kind: String,
    val writableValueLength: Int? = null,
)

/**
 * Callback interface for [BluetoothTransport] implementations.
 *
 * The transport calls these methods asynchronously from its internal threads;
 * implementors are responsible for posting to the appropriate thread if needed.
 */
interface TransportListener {
    /** Transport is connected and ready to send/receive. */
    fun onReady(info: TransportInfo)

    /** Incoming protocol bytes from the transport. */
    fun onMessage(bytes: ByteArray)

    /** Transport disconnected or connection lost. [reason] may be null for clean shutdown. */
    fun onDisconnected(reason: String?)

    /** Debug log message from the transport. */
    fun onLog(message: String)
}

/**
 * Brand-agnostic Bluetooth transport abstraction.
 *
 * Each transport implementation (SPP, BLE GATT) handles the wire-level
 * connection, framing, and byte delivery. It knows nothing about protocol
 * semantics (Tandem, TLV, etc.) — only raw bytes in and out.
 *
 * Implementations:
 *   - [SppTransport]: RFCOMM socket with Sony SPP framing (0x3E/0x3C, escape, checksum, ACK).
 *   - GattTransport (Phase 2): BLE GATT characteristic read/write/notify.
 */
interface BluetoothTransport {
    /** Human-readable identifier: "SPP", "GATT_HPC", etc. */
    val name: String

    /** Static info about this transport connection. */
    val info: TransportInfo

    /**
     * Send protocol bytes. The transport handles any wire-level framing
     * (escape, checksum, chunking) internally.
     */
    fun send(bytes: ByteArray)

    /**
     * Cleanly close the transport. Does NOT fire [TransportListener.onDisconnected];
     * the caller already knows it initiated the close. Only unexpected disconnects
     * (IO errors, remote disconnect) fire the listener callback.
     */
    fun close()
}
