package com.fantamomo.mc.amongus.manager.waypoint

import net.minecraft.core.Vec3i
import org.bukkit.Location

fun interface WaypointLocationProvider : WaypointPosProvider {
    fun location(): Location
    override fun pos(): Vec3i = location().let { Vec3i(it.blockX, it.blockY, it.blockZ) }
}