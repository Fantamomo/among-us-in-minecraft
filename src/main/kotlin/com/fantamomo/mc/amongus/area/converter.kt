package com.fantamomo.mc.amongus.area

import com.fantamomo.mc.amongus.util.SerializableLocation.Companion.fromBukkit
import org.bukkit.Location
import java.util.*
import kotlin.math.max
import kotlin.math.min

private fun minAndMax(l1: Location, l2: Location) =
    Location(null, min(l1.x, l2.x), min(l1.y, l2.y), min(l1.z, l2.z)) to
            Location(null, max(l1.x, l2.x), max(l1.y, l2.y), max(l1.z, l2.z))
private fun minAndMaxNullable(l1: Location?, l2: Location?): Pair<Location?, Location?> {
    if (l1 == null || l2 == null) return Pair(null, null)
    return minAndMax(l1, l2)
}

fun GameArea.toDTO(): GameAreaDTO {
    val (minCorner, maxCorner) = minAndMaxNullable(minCorner, maxCorner)
    val (cameraJoinPointMin, cameraJoinPointMax) = minAndMaxNullable(cameraJoinPointMin, cameraJoinPointMax)
    val (lightPosMin, lightPosMax) = minAndMaxNullable(lightPosMin, lightPosMax)
    return GameAreaDTO(
        name = name,
        uuid = uuid.toString(),
        minCorner = minCorner?.let(::fromBukkit),
        maxCorner = maxCorner?.let(::fromBukkit),
        lobbySpawn = lobbySpawn?.let(::fromBukkit),
        meetingBlock = meetingBlock?.let(::fromBukkit),
        ejectedFallPoint = ejectedFallPoint?.let(::fromBukkit),
        ejectedViewPoint = ejectedViewPoint?.let(::fromBukkit),
        cameraJoinPointMin = cameraJoinPointMin?.let(::fromBukkit),
        cameraJoinPointMax = cameraJoinPointMax?.let(::fromBukkit),
        lightPosMin = lightPosMin?.let(::fromBukkit),
        lightPosMax = lightPosMax?.let(::fromBukkit),
        cams = cams.mapValues { fromBukkit(it.value) },
        vents = vents.map {
            VentGroupDTO(
                id = it.id,
                vents = it.vents.map(::fromBukkit)
            )
        },
        lightLevers = lightLevers.mapTo(mutableSetOf()) { fromBukkit(it.toBlockLocation()) }
    )
}

fun GameAreaDTO.toGameArea(): GameArea {
    val area = GameArea(name, UUID.fromString(uuid))

    area.minCorner = minCorner?.toBukkit()
    area.maxCorner = maxCorner?.toBukkit()
    area.lobbySpawn = lobbySpawn?.toBukkit()
    area.meetingBlock = meetingBlock?.toBukkit()
    area.ejectedFallPoint = ejectedFallPoint?.toBukkit()
    area.ejectedViewPoint = ejectedViewPoint?.toBukkit()
    area.cameraJoinPointMin = cameraJoinPointMin?.toBukkit()
    area.cameraJoinPointMax = cameraJoinPointMax?.toBukkit()
    area.lightPosMin = lightPosMin?.toBukkit()
    area.lightPosMax = lightPosMax?.toBukkit()

    area.cams.putAll(cams.mapValues { it.value.toBukkit() })
    area.vents.addAll(
        vents.map {
            VentGroup(
                id = it.id,
                vents = it.vents.map { loc -> loc.toBukkit() }.toSet()
            )
        }
    )
    area.lightLevers.addAll(lightLevers.map { it.toBukkit() })

    return area
}
