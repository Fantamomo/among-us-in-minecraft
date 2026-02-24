package com.fantamomo.mc.amongus.manager.waypoint

import net.minecraft.core.Vec3i

fun interface WaypointPosProvider {
    fun pos(): Vec3i
}