package com.fantamomo.mc.amongus.area

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.util.SerializableLocation
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*

object GameAreaManager {

    private val directory: Path = AmongUs.dataPath.resolve("areas")
    private val areas: MutableMap<UUID, GameArea> = mutableMapOf()
    private val logger = LoggerFactory.getLogger("GameAreaManager")

    private val json = Json {
        prettyPrint = AmongUs.IN_DEVELOPMENT
        ignoreUnknownKeys = true
    }

    init {
        // Trigger one serialization to force initialization of all
        // kotlinx.serialization related classes that are required at runtime.
        //
        // This prevents a java.lang.NoClassDefFoundError that can occur when the
        // plugin JAR is hot-reloaded or replaced while the plugin is still loaded,
        // because some serialization classes would otherwise be loaded lazily
        // only on first real use.
        json.encodeToString(
            GameAreaDTO(
                "dummy", "no-uuid",
                SerializableLocation(0.0, 0.0, 0.0, 0f, 0f),
                null, null, null,
                null, null, null,
                null, null, null,
                null, null, null,
                null, null, null,
                null,
                mapOf(), listOf(), setOf(), mapOf()
            )
        )
    }

    fun loadAreas() {
        if (!directory.exists()) directory.createDirectories()

        directory.listDirectoryEntries("*.json").forEach { file ->
            try {
                val dto = json.decodeFromString<GameAreaDTO>(file.readText())
                val area = dto.toGameArea()
                areas[area.uuid] = area
                logger.info("Loaded area ${area.name}")
            } catch (e: Exception) {
                logger.error("Failed to load area ${file.fileName}", e)
            }
        }
    }

    fun saveArea(area: GameArea) {
        if (!directory.exists()) directory.createDirectories()

        val file = directory.resolve("${area.uuid}.json")
        val dto = area.toDTO()

        try {
            file.writeText(json.encodeToString(dto))
            logger.info("Saved area ${area.name}")
        } catch (e: Exception) {
            logger.error("Failed to save area ${area.name}", e)
        }
    }

    fun saveAll() = areas.values.forEach(::saveArea)

    fun getArea(uuid: UUID): GameArea? = areas[uuid]

    fun getArea(name: String): GameArea? = areas.values.find { it.name == name }

    fun getAreas(): Collection<GameArea> = areas.values

    fun registerArea(area: GameArea) {
        areas[area.uuid] = area
        saveArea(area)
    }

    fun createNewArea(name: String): Boolean {
        if (areas.values.any { it.name == name }) return false
        val area = GameArea(name, UUID.randomUUID())
        registerArea(area)
        return true
    }
}
