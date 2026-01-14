package com.fantamomo.mc.amongus.util

import org.bukkit.Location
import java.util.*

enum class BlockEdge {
    BOTTOM_NORTH,
    BOTTOM_SOUTH,
    BOTTOM_EAST,
    BOTTOM_WEST,
    TOP_NORTH,
    TOP_SOUTH,
    TOP_EAST,
    TOP_WEST,
    NORTH_EAST,
    NORTH_WEST,
    SOUTH_EAST,
    SOUTH_WEST
}


fun getEdgeLocation(
    blockLocation: Location,
    edges: EnumSet<BlockEdge>,
    step: Double
): List<Location> {
    val world = blockLocation.getWorld()

    val x = blockLocation.blockX.toDouble()
    val y = blockLocation.blockY.toDouble()
    val z = blockLocation.blockZ.toDouble()

    val locations = mutableListOf<Location>()

    for (edge in edges) {
        var i = 0.0
        while (i <= 1) {
            val loc = when (edge) {
                BlockEdge.BOTTOM_NORTH -> Location(world, x + i, y, z)
                BlockEdge.BOTTOM_SOUTH -> Location(world, x + i, y, z + 1)
                BlockEdge.BOTTOM_WEST -> Location(world, x, y, z + i)
                BlockEdge.BOTTOM_EAST -> Location(world, x + 1, y, z + i)
                BlockEdge.TOP_NORTH -> Location(world, x + i, y + 1, z)
                BlockEdge.TOP_SOUTH -> Location(world, x + i, y + 1, z + 1)
                BlockEdge.TOP_WEST -> Location(world, x, y + 1, z + i)
                BlockEdge.TOP_EAST -> Location(world, x + 1, y + 1, z + i)
                BlockEdge.NORTH_WEST -> Location(world, x, y + i, z)
                BlockEdge.NORTH_EAST -> Location(world, x + 1, y + i, z)
                BlockEdge.SOUTH_WEST -> Location(world, x, y + i, z + 1)
                BlockEdge.SOUTH_EAST -> Location(world, x + 1, y + i, z + 1)
            }

            locations.add(loc)
            i += step
        }
    }

    return locations
}