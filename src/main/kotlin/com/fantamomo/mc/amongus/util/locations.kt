package com.fantamomo.mc.amongus.util

import org.bukkit.Location
import org.bukkit.World

fun Location.isSameWorld(other: Location): Boolean =
    this.getWorld() == other.getWorld()

fun Location.getSameWorldOrThrow(other: Location): World? {
    if (isSameWorld(other)) return this.world
    else throw IllegalArgumentException("Locations must be in the same world")
}

fun Location.isBetween(min: Location, max: Location): Boolean {
    if (!isSameWorld(min) || !isSameWorld(max)) {
        return false
    }

    return this.x >= min.x && this.x <= max.x && this.y >= min.y && this.y <= max.y && this.z >= min.z && this.z <= max.z
}

fun Location.isSameBlockPosition(other: Location): Boolean =
    isSameWorld(other) && this.blockX == other.blockX && this.blockY == other.blockY && this.blockZ == other.blockZ

fun centerLocationOf(one: Location, two: Location): Location =
    Location(one.getSameWorldOrThrow(two), (one.x + two.x) / 2, (one.y + two.y) / 2, (one.z + two.z) / 2)

fun Location.removeWorld(): Location = Location(null, x, y, z)