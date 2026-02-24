package com.fantamomo.mc.amongus.manager.waypoint

import net.minecraft.core.Vec3i
import org.bukkit.Location

data class FixedWaypointPosProvider(val pos: Vec3i) : WaypointPosProvider {
    constructor(location: Location) : this(Vec3i(location.blockX, location.blockY, location.blockZ))
    override fun pos() = pos
}