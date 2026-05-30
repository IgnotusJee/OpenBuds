package dev.ignotus.openbuds.lsposed

/**
 * Manages a MAC address whitelist file shared between the app process and
 * the LSPosed module running in com.milink.service.
 *
 * The whitelist is stored at /sdcard/headset_whitelist.txt, one MAC per line,
 * with '#' comment lines supported. Both processes can read/write this file.
 */
object DeviceWhitelist {

    private const val WHITELIST_PATH = "/sdcard/headset_whitelist.txt"

    /**
     * Reads the whitelist and returns a set of normalized (uppercase, trimmed) MAC addresses.
     */
    fun read(): Set<String> {
        return try {
            val file = java.io.File(WHITELIST_PATH)
            if (!file.exists()) return emptySet()
            file.readLines()
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    /**
     * Adds a MAC to the whitelist. Does not duplicate existing entries.
     */
    fun add(mac: String) {
        try {
            val file = java.io.File(WHITELIST_PATH)
            file.parentFile?.mkdirs()
            val existing = read().toMutableSet()
            existing.add(mac.trim().uppercase())
            file.writeText(existing.joinToString("\n") + "\n")
            file.setReadable(true, false)
        } catch (_: Exception) {
            // Silent fail — file may not be writable
        }
    }

    /**
     * Removes a MAC from the whitelist.
     */
    fun remove(mac: String) {
        try {
            val file = java.io.File(WHITELIST_PATH)
            if (!file.exists()) return
            val existing = read().toMutableSet()
            existing.remove(mac.trim().uppercase())
            file.writeText(existing.joinToString("\n") + "\n")
        } catch (_: Exception) {
            // Silent fail
        }
    }

    /**
     * Checks whether a MAC is in the whitelist.
     */
    fun contains(mac: String): Boolean {
        return read().contains(mac.trim().uppercase())
    }
}
