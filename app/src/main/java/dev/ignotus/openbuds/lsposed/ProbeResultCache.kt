package dev.ignotus.openbuds.lsposed

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

object ProbeResultCache {
    private const val TAG = "OpenBuds"
    private const val FILE_NAME = "lsposed_probe_results.json"

    data class ProbeStatus(
        val className: String,
        val found: Boolean,
        val methodsFound: List<String> = emptyList(),
        val methodsNotFound: List<String> = emptyList(),
        val timestamp: Long = System.currentTimeMillis(),
    )

    private val lock = Any()
    private val results = mutableMapOf<String, ProbeStatus>()

    // Shared location accessible from both system processes and the app process.
    // /data/local/tmp/ is world-readable/writable on all Android devices.
    private val sharedFile: java.io.File
        get() = java.io.File("/data/local/tmp", FILE_NAME)

    fun markFound(className: String) {
        synchronized(lock) {
            results[className] = (results[className] ?: ProbeStatus(className, true)).copy(found = true)
        }
    }

    fun markNotFound(className: String) {
        synchronized(lock) {
            results[className] = (results[className] ?: ProbeStatus(className, false)).copy(found = false)
        }
    }

    fun markMethodFound(className: String, methodName: String) {
        synchronized(lock) {
            val existing = results[className] ?: ProbeStatus(className, true)
            results[className] = existing.copy(
                found = true,
                methodsFound = (existing.methodsFound + methodName).distinct(),
            )
        }
    }

    fun markMethodNotFound(className: String, methodName: String) {
        synchronized(lock) {
            val existing = results[className] ?: ProbeStatus(className, false)
            results[className] = existing.copy(
                methodsNotFound = (existing.methodsNotFound + methodName).distinct(),
            )
        }
    }

    fun allResults(): List<ProbeStatus> = synchronized(lock) {
        results.values.toList()
    }

    fun isCompatible(): String = synchronized(lock) {
        val all = results.values.toList()
        if (all.isEmpty()) return "Unknown"
        val found = all.count { it.found }
        val total = all.size
        return when {
            found == total -> "Compatible"
            found > total / 2 -> "Partial"
            found > 0 -> "Incompatible"
            else -> "Unknown"
        }
    }

    fun lastProbeTime(): Long = synchronized(lock) {
        results.values.maxOfOrNull { it.timestamp } ?: 0L
    }

    // ---- Shared-file persistence (cross-process) ----

    /** Persist current in-memory results to the shared file, merging with
     *  any existing results written by other processes. */
    fun persistShared() {
        synchronized(lock) {
            try {
                val existing = loadFromFile()
                for ((name, status) in results) {
                    existing[name] = status
                }
                results.putAll(existing)
                sharedFile.parentFile?.mkdirs()
                sharedFile.writeText(toJson(existing))
                sharedFile.setReadable(true, false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist probe results", e)
            }
        }
    }

    /** Load results from the shared file into memory. Safe to call from any process. */
    fun loadShared() {
        synchronized(lock) {
            try {
                if (!sharedFile.exists()) return
                val loaded = loadFromFile()
                results.putAll(loaded)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load probe results", e)
            }
        }
    }

    // ---- Legacy Context-based save/load (kept for compatibility) ----

    fun save(context: android.content.Context) {
        synchronized(lock) {
            try {
                val json = toJson(results)
                java.io.File(context.filesDir, FILE_NAME).writeText(json)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save $FILE_NAME", e)
            }
        }
    }

    fun load(context: android.content.Context) {
        // Try shared file first (system processes write there), fall back to app-local.
        loadShared()
        if (results.isNotEmpty()) return
        synchronized(lock) {
            try {
                val file = java.io.File(context.filesDir, FILE_NAME)
                if (!file.exists()) return
                val loaded = loadFromFile(file)
                results.putAll(loaded)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load $FILE_NAME", e)
            }
        }
    }

    // ---- Internal helpers ----

    private fun loadFromFile(file: java.io.File = sharedFile): MutableMap<String, ProbeStatus> {
        val map = mutableMapOf<String, ProbeStatus>()
        if (!file.exists()) return map
        val json = org.json.JSONObject(file.readText())
        for (key in json.keys()) {
            val obj = json.getJSONObject(key)
            map[key] = ProbeStatus(
                className = key,
                found = obj.getBoolean("found"),
                methodsFound = obj.getString("methodsFound").split(",").filter { it.isNotEmpty() },
                methodsNotFound = obj.getString("methodsNotFound").split(",").filter { it.isNotEmpty() },
                timestamp = obj.getLong("timestamp"),
            )
        }
        return map
    }

    private fun toJson(results: Map<String, ProbeStatus>): String {
        val json = org.json.JSONObject()
        for ((name, status) in results) {
            val obj = org.json.JSONObject()
            obj.put("found", status.found)
            obj.put("methodsFound", status.methodsFound.joinToString(","))
            obj.put("methodsNotFound", status.methodsNotFound.joinToString(","))
            obj.put("timestamp", status.timestamp)
            json.put(name, obj)
        }
        return json.toString()
    }
}
