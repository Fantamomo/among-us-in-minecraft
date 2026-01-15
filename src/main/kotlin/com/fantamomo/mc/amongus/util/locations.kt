package com.fantamomo.mc.amongus.util

import org.bukkit.Location

fun Location.isBetween(min: Location, max: Location): Boolean {
    if (getWorld() != min.getWorld() || getWorld() != max.getWorld()) {
        return false
    }

    return this.x >= min.x && this.x <= max.x && this.y >= min.y && this.y <= max.y && this.z >= min.z && this.z <= max.z
}

fun Location.isSameBlockPosition(other: Location): Boolean = this.blockX == other.blockX && this.blockY == other.blockY && this.blockZ == other.blockZ