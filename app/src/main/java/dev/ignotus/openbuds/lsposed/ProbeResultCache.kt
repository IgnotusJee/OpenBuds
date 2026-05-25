package dev.ignotus.openbuds.lsposed

import android.content.Context
import org.json.JSONObject
import java.io.File

object ProbeResultCache {
    private const val FILE_NAME = "lsposed_probe_results.json"

    data class ProbeStatus(
        val className: String,
        val found: Boolean,
        val methodsFound: List<String> = emptyList(),
        val methodsNotFound: List<String> = emptyList(),
        val timestamp: Long = System.currentTimeMillis(),
    )

    private val results = mutableMapOf<String, ProbeStatus>()

    fun markFound(className: String) {
        results[className] = (results[className] ?: ProbeStatus(className, true)).copy(found = true)
    }

    fun markNotFound(className: String) {
        results[className] = (results[className] ?: ProbeStatus(className, false)).copy(found = false)
    }

    fun markMethodFound(className: String, methodName: String) {
        val existing = results[className] ?: ProbeStatus(className, true)
        results[className] = existing.copy(
            found = true,
            methodsFound = existing.methodsFound + methodName,
        )
    }

    fun markMethodNotFound(className: String, methodName: String) {
        val existing = results[className] ?: ProbeStatus(className, false)
        results[className] = existing.copy(
            methodsNotFound = existing.methodsNotFound + methodName,
        )
    }

    fun allResults(): List<ProbeStatus> = results.values.toList()

    fun isCompatible(): String {
        val all = allResults()
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

    fun lastProbeTime(): Long = results.values.maxOfOrNull { it.timestamp } ?: 0L

    fun save(context: Context) {
        try {
            val json = JSONObject()
            for ((name, status) in results) {
                val obj = JSONObject()
                obj.put("found", status.found)
                obj.put("methodsFound", status.methodsFound.joinToString(","))
                obj.put("methodsNotFound", status.methodsNotFound.joinToString(","))
                obj.put("timestamp", status.timestamp)
                json.put(name, obj)
            }
            File(context.filesDir, FILE_NAME).writeText(json.toString())
        } catch (_: Exception) {}
    }

    fun load(context: Context) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return
            val json = JSONObject(file.readText())
            for (key in json.keys()) {
                val obj = json.getJSONObject(key)
                results[key] = ProbeStatus(
                    className = key,
                    found = obj.getBoolean("found"),
                    methodsFound = obj.getString("methodsFound").split(",").filter { it.isNotEmpty() },
                    methodsNotFound = obj.getString("methodsNotFound").split(",").filter { it.isNotEmpty() },
                    timestamp = obj.getLong("timestamp"),
                )
            }
        } catch (_: Exception) {}
    }
}
