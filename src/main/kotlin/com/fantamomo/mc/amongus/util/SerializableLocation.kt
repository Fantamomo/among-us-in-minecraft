package com.fantamomo.mc.amongus.util

import kotlinx.serialization.Serializable
import org.bukkit.Bukkit
import org.bukkit.Location
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@Serializable
data class SerializableLocation(
    val world: Uuid?,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float = 0f,
    val pitch: Float = 0f
) {
    fun toBukkit(): Location =
        Location(
            world?.let { Bukkit.getWorld(it.toJavaUuid()) },
            x, y, z,
            yaw, pitch
        )

    companion object {
        fun fromBukkit(loc: Location): SerializableLocation =
            SerializableLocation(
                world = loc.world?.uid?.toKotlinUuid(),
                x = loc.x,
                y = loc.y,
                z = loc.z,
                yaw = loc.yaw,
                pitch = loc.pitch
            )
    }
}
