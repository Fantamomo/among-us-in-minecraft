package com.fantamomo.mc.amongus.area

import org.bukkit.Location
import org.bukkit.World

data class VentGroup(
    val id: Int,
    val vents: Set<Location>
) {
    fun withWorld(world: World): VentGroup {
        return VentGroup(id, vents.map { it.clone().apply { this.world = world } }.toSet())
    }
}