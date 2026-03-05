package com.fantamomo.mc.amongus.player

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.AmongUsConstants
import com.fantamomo.mc.amongus.util.safeCreateDirectories
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bukkit.Bukkit
import org.bukkit.Location
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.io.path.notExists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

object LastPlayerLocationManager {
    private val logger = LoggerFactory.getLogger("AmongUs-LastPlayerLocationManager")
    private val direction = AmongUs.dataPath.resolve("data").resolve("world").resolve("last_locations")

    private val data: MutableMap<Uuid, LastPlayerLocation> = mutableMapOf()

    private val json = Json {
        prettyPrint = AmongUsConstants.IN_DEVELOPMENT
        ignoreUnknownKeys = true
    }

    fun load() {
        direction.safeCreateDirectories()
        val respawnWorld = AmongUs.server.respawnWorld
        val file = direction.resolve("${respawnWorld.uid}.json")
        if (file.notExists()) return
        try {
            val content = file.readText()
            val locations = json.decodeFromString<Map<Uuid, LastPlayerLocation>>(content)
            data.putAll(locations)
        } catch (e: Exception) {
            logger.error("Failed to load last player locations", e)
        }
    }

    fun save() {
        val respawnWorld = AmongUs.server.respawnWorld
        try {
            val file = direction.resolve("${respawnWorld.uid}.json")
            val content = json.encodeToString(data)
            file.writeText(content)
        } catch (e: Exception) {
            logger.error("Failed to save last player locations", e)
        }
    }

    fun get(uuid: Uuid) = data[uuid]

    fun set(uuid: Uuid, loc: LastPlayerLocation) {
        data[uuid] = loc
    }

    fun set(uuid: UUID, loc: Location) {
        require(loc.world != null) { "Location must have a world" }
        set(
            uuid.toKotlinUuid(),
            loc.run {
                LastPlayerLocation(
                    world.uid.toKotlinUuid(),
                    x,
                    y,
                    z,
                    yaw,
                    pitch
                )
            }
        )
    }

    fun remove(uuid: Uuid) {
        data.remove(uuid)
    }

    @Serializable
    class LastPlayerLocation(
        val world: Uuid,
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pitch: Float
    ) {
        fun toLocation() = Location(
            Bukkit.getWorld(world.toJavaUuid()), x, y, z, yaw, pitch
        )

    }
}