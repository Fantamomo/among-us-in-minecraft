package com.fantamomo.mc.amongus.manager.waypoint

import net.minecraft.core.Vec3i
import org.bukkit.Location

class MutableWaypointPosProvider private constructor(private var value: Any) : WaypointPosProvider {

    constructor(location: Location) : this(location as Any)

    constructor(pos: Vec3i) : this(pos as Any)

    var pos: Vec3i
        get() = pos()
        set(value) {
            this.value = value
        }
    var location: Location
        get() = when (val v = value) {
            is Location -> v
            is Vec3i -> Location(null, v.x.toDouble(), v.y.toDouble(), v.z.toDouble())
            else -> throw IllegalStateException("Invalid value type")
        }
        set(value) {
            this.value = value
        }

    override fun pos() = when (val v = value) {
        is Vec3i -> v
        is Location -> Vec3i(v.blockX, v.blockY, v.blockZ)
        else -> throw IllegalStateException("Invalid value type")
    }
}