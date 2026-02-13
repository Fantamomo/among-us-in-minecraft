package com.fantamomo.mc.amongus.player

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.util.safeCreateDirectories
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.notExists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.uuid.Uuid

object PlayerDataManager {

    private val directory: Path = AmongUs.dataPath.resolve("data").resolve("players")
    private val logger = LoggerFactory.getLogger("AmongUs-PlayerDataManager")

    private val data: MutableMap<Uuid, PersistencePlayerData> = mutableMapOf()

    private val json = Json {
        prettyPrint = AmongUs.IN_DEVELOPMENT
        ignoreUnknownKeys = true
    }

    init {
        directory.safeCreateDirectories()
    }

    fun get(uuid: Uuid) = data[uuid] ?: loadOrCreate(uuid).also { data[uuid] = it }

    private fun loadOrCreate(uuid: Uuid): PersistencePlayerData {
        val file = directory.resolve("$uuid.json")
        if (file.notExists()) return PersistencePlayerData(uuid, null)
        try {
            val json = json.parseToJsonElement(file.readText())
            return PersistencePlayerData(uuid, json.jsonObject)
        } catch (e: Exception) {
            logger.error("Failed to load player data for $uuid", e)
            return PersistencePlayerData(uuid, null)
        }
    }

    fun save(playerData: PersistencePlayerData) {
        try {
            val file = directory.resolve("${playerData.uuid}.json")
            file.writeText(json.encodeToString(playerData.toJson()))
        } catch (e: Exception) {
            logger.error("Failed to save player data for ${playerData.uuid}", e)
        }
    }

    fun save(uuid: Uuid) = data[uuid]?.let { save(it) }

    fun saveAll() {
        data.values.forEach(::save)
    }
}