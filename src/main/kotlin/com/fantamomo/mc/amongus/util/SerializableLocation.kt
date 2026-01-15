package com.fantamomo.mc.amongus.util

import kotlinx.serialization.Serializable
import org.bukkit.Location

@Serializable
data class SerializableLocation(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float = 0f,
    val pitch: Float = 0f
) {
    fun toBukkit(): Location =
        Location(
            null,
            x, y, z,
            yaw, pitch
        )

    companion object {
        fun fromBukkit(loc: Location): SerializableLocation =
            SerializableLocation(
                x = loc.x,
                y = loc.y,
                z = loc.z,
                yaw = loc.yaw,
                pitch = loc.pitch
            )
    }
}
