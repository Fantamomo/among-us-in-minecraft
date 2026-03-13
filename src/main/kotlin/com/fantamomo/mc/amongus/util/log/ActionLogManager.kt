@file:Suppress("OPT_IN_USAGE")

package com.fantamomo.mc.amongus.util.log

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.AmongUsConstants
import com.fantamomo.mc.amongus.util.safeCreateDirectories
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import kotlin.io.path.writeText

object ActionLogManager {
    private val logger = LoggerFactory.getLogger("AmongUsActionLogManager")
    private val TYPE_REGEX = Regex("^[a-zA-Z0-9_]+$")
    private val direction = AmongUs.dataPath.resolve("logs")

    private val logs: MutableMap<String, MutableList<ActionLog>> = mutableMapOf()
    private val logToType: MutableMap<ActionLog, String> = mutableMapOf()

    private val json = Json {
        prettyPrint = AmongUsConstants.IN_DEVELOPMENT
        ignoreUnknownKeys = true
    }

    fun register(type: String, log: ActionLog) {
        require(TYPE_REGEX.matches(type)) { "Invalid log type: $type" }
        logs.getOrPut(type) { mutableListOf() }.add(log)
        logToType[log] = type
    }

    fun saveAndRemove(log: ActionLog) {
        save(log)
        logs[logToType[log]]?.remove(log)
        logToType.remove(log)
    }

    fun save(log: ActionLog) {
        val type = logToType[log] ?: throw IllegalArgumentException("Log not registered")
        require(TYPE_REGEX.matches(type)) { "Invalid log type: $type" }
        val file = direction.resolve(type).resolve("${log.id}.json")
        file.parent.safeCreateDirectories()
        val data = buildJsonObject {
            put("metadata", anyMapToJsonObject(log.metadata))
            put("log", buildJsonArray {
                for (type in log.entries) {
                    addJsonObject {
                        put("type", type.type.action)
                        put("timestamp", type.timestamp.toString())
                        val json = type.type.toJson()
                        if (json != null) put("data", json)
                    }
                }
            })
        }
        try {
            val text = this.json.encodeToString(JsonObject.serializer(), data)
            file.writeText(text)
        } catch (e: Exception) {
            logger.error("Failed to save log $log", e)
        }
    }

    private fun anyMapToJsonObject(map: Map<String, Any?>): JsonObject = buildJsonObject {
        for ((key, value) in map) {
            when (value) {
                is JsonElement -> put(key, value)
                is Number -> put(key, value)
                is String -> put(key, value)
                is Boolean -> put(key, value)
                is Map<*, *> -> put(key, anyMapToJsonObject(value.mapKeys { (k, _) -> k.toString() }))
                null -> put(key, null)
                else -> put(key, value.toString())
            }
        }
    }

    fun saveAll(remove: Boolean = true) {
        for (logs in logs.values) {
            for (log in logs) {
                save(log)
            }
        }
        if (remove) {
            logs.clear()
            logToType.clear()
        }
    }
}