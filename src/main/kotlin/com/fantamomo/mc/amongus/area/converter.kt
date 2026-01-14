package com.fantamomo.mc.amongus.area

import com.fantamomo.mc.amongus.util.SerializableLocation.Companion.fromBukkit
import org.bukkit.Location
import java.util.*
import kotlin.math.max
import kotlin.math.min

fun GameArea.toDTO(): GameAreaDTO {
    val (minCorner, maxCorner) = run {
        val c1 = minCorner
        val c2 = maxCorner
        if (c1 == null || c2 == null) return@run Pair(null, null)
        Location(null, min(c1.x, c2.x), min(c1.y, c2.y), min(c1.z, c2.z)) to
                Location(null, max(c1.x, c2.x), max(c1.y, c2.y), max(c1.z, c2.z))
    }
    val (cameraJoinPointMin, cameraJoinPointMax) = run {
        val c1 = cameraJoinPointMin
        val c2 = cameraJoinPointMax
        if (c1 == null || c2 == null) return@run Pair(null, null)
        Location(null, min(c1.x, c2.x), min(c1.y, c2.y), min(c1.z, c2.z)) to
                Location(null, max(c1.x, c2.x), max(c1.y, c2.y), max(c1.z, c2.z))
    }
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
        cams = cams.mapValues { fromBukkit(it.value) },
        vents = vents.map {
            VentGroupDTO(
                id = it.id,
                vents = it.vents.map(::fromBukkit)
            )
        }
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

    area.cams.putAll(cams.mapValues { it.value.toBukkit() })
    area.vents.addAll(
        vents.map {
            VentGroup(
                id = it.id,
                vents = it.vents.map { loc -> loc.toBukkit() }.toSet()
            )
        }
    )

    return area
}
