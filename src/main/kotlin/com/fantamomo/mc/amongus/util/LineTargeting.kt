package com.fantamomo.mc.amongus.util

import org.bukkit.Location

const val DEFAULT_DISTANCE_SCALE: Double = 0.025

fun <T> getClosestLocationOnLine(
    playerLoc: Location,
    locations: Map<Location, T>,
    baseLineDistance: Double,
    distanceScale: Double = DEFAULT_DISTANCE_SCALE
): T? {
    val world = playerLoc.world ?: return null
    val origin = playerLoc.toVector()
    val direction = playerLoc.direction.normalize()

    var closestValue: T? = null
    var closestDistance = Double.MAX_VALUE

    for ((loc, value) in locations) {
        if (loc.world != world) continue

        val toTarget = loc.toVector().subtract(origin)

        val distanceAlongLine = toTarget.dot(direction)
        if (distanceAlongLine <= 0 || distanceAlongLine >= closestDistance) continue

        val perpendicularDistance = toTarget
            .subtract(direction.clone().multiply(distanceAlongLine))
            .length()

        val allowedDistance =
            baseLineDistance + distanceAlongLine * distanceScale

        if (perpendicularDistance <= allowedDistance) {
            closestDistance = distanceAlongLine
            closestValue = value
        }
    }

    return closestValue
}

fun getClosestLocationOnLine(
    playerLoc: Location,
    locations: List<Location>,
    baseLineDistance: Double,
    distanceScale: Double = DEFAULT_DISTANCE_SCALE
): Location? = getClosestLocationOnLine(
    playerLoc,
    locations.associateWith { it },
    baseLineDistance,
    distanceScale
)