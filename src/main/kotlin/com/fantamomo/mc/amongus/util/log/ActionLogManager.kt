@file:Suppress("OPT_IN_USAGE")

package com.fantamomo.mc.amongus.util.log

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.AmongUsConstants
import com.fantamomo.mc.amongus.util.safeCreateDirectories
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.net.URL
import kotlin.io.path.writeText
import kotlin.uuid.Uuid

object ActionLogManager {
    private val logger = LoggerFactory.getLogger("AmongUsActionLogManager")
    private val TYPE_REGEX = Regex("^[a-zA-Z0-9_]+$")
    private val direction = AmongUs.dataPath.resolve("logs")

    private val logs: MutableMap<String, MutableList<ActionLog>> = mutableMapOf()
    private val logToType: MutableMap<ActionLog, String> = mutableMapOf()

    private val json = Json {
        prettyPrint = AmongUsConstants.IN_DEVELOPMENT
    }
    private val jsonCompact = Json

    fun register(type: String, log: ActionLog) {
        require(TYPE_REGEX.matches(type)) { "Invalid log type: $type" }
        require(!logToType.containsKey(log)) { "Log already registered: $log" }
        logs.getOrPut(type) { mutableListOf() }.add(log)
        logToType[log] = type
    }

    fun saveAndRemove(log: ActionLog) {
        val type = logToType[log] ?: throw IllegalArgumentException("Log not registered: $log")
        val data = toJson(log)
        save(type, log.id, data)
        logs[logToType[log]]?.remove(log)
        logToType.remove(log)
    }

    suspend fun saveUploadAndRemove(log: ActionLog): URL? {
        val type = logToType[log] ?: throw IllegalArgumentException("Log not registered: $log")
        val data = toJson(log)
        save(type, log.id, data)
        logs[logToType[log]]?.remove(log)
        logToType.remove(log)
        return upload(data)
    }

    private fun toJson(log: ActionLog): JsonObject = buildJsonObject {
        put("createdAt", log.createdAt.toString())
        put("metadata", anyMapToJsonObject(log.metadata))
        if (log.customData.isNotEmpty()) put("customData", anyMapToJsonObject(log.customData))
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

    private suspend fun upload(data: JsonObject): URL? {
        if (!ActionLogUploader.enabled()) return null
        val text = jsonCompact.encodeToString(data)
        return ActionLogUploader.upload(text)
    }

    fun save(type: String, id: Uuid, data: JsonObject) {
        require(TYPE_REGEX.matches(type)) { "Invalid log type: $type" }
        val file = direction.resolve(type).resolve("${id}.json")
        file.parent.safeCreateDirectories()
        try {
            val text = this.json.encodeToString(JsonObject.serializer(), data)
            file.writeText(text)
        } catch (e: Exception) {
            logger.error("Failed to save log $type:$id", e)
        }
    }

    private fun anyMapToJsonObject(map: Map<String, Any?>): JsonObject = buildJsonObject {
        for ((key, value) in map) {
            when (value) {
                is JsonElement -> put(key, value)
                is Number -> put(key, value)
                is String -> put(key, value)
                is Boolean -> put(key, value)
                is Array<*> -> put(key, anyCollectionToJsonArray(value.toList()))
                is Collection<*> -> put(key, anyCollectionToJsonArray(value))
                is Map<*, *> -> put(key, anyMapToJsonObject(value.mapKeys { (k, _) -> k.toString() }))
                null -> put(key, null)
                else -> put(key, value.toString())
            }
        }
    }

    private fun anyCollectionToJsonArray(collection: Collection<Any?>): JsonArray = buildJsonArray {
        for (element in collection) {
            when (element) {
                is JsonElement -> add(element)
                is Number -> add(element)
                is String -> add(element)
                is Boolean -> add(element)
                is Array<*> -> add(anyCollectionToJsonArray(element.toList()))
                is Collection<*> -> add(anyCollectionToJsonArray(element))
                is Map<*, *> -> add(anyMapToJsonObject(element.mapKeys { (k, _) -> k.toString() }))
                null -> add(null)
                else -> add(element.toString())
            }
        }
    }

    fun saveAll(remove: Boolean = true) {
        for ((type, logs) in logs) {
            for (log in logs) {
                val data = toJson(log)
                save(type, log.id, data)
            }
        }
        if (remove) {
            logs.clear()
            logToType.clear()
        }
    }
}