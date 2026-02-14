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
    val (meetingRoomMin, meetingRoomMax) = minAndMaxNullable(meetingRoomMin, meetingRoomMax)
    return GameAreaDTO(
        name = name,
        uuid = uuid.toString(),
        minCorner = minCorner?.let(::fromBukkit),
        maxCorner = maxCorner?.let(::fromBukkit),
        lobbySpawn = lobbySpawn?.let(::fromBukkit),
        gameSpawn = gameSpawn?.let(::fromBukkit),
        meetingRoomMin = meetingRoomMin?.let(::fromBukkit),
        meetingRoomMax = meetingRoomMax?.let(::fromBukkit),
        meetingBlock = meetingBlock?.let(::fromBukkit),
        ejectedFallPoint = ejectedFallPoint?.let(::fromBukkit),
        ejectedViewPoint = ejectedViewPoint?.let(::fromBukkit),
        cameraJoinPointMin = cameraJoinPointMin?.let(::fromBukkit),
        cameraJoinPointMax = cameraJoinPointMax?.let(::fromBukkit),
        lightPosMin = lightPosMin?.let(::fromBukkit),
        lightPosMax = lightPosMax?.let(::fromBukkit),
        seismicStabilizers1 = seismicStabilizers1?.let(::fromBukkit),
        seismicStabilizers2 = seismicStabilizers2?.let(::fromBukkit),
        seismicStabilizers1Particle = seismicStabilizers1Particle?.let(::fromBukkit),
        seismicStabilizers2Particle = seismicStabilizers2Particle?.let(::fromBukkit),
        communications = communications?.let(::fromBukkit),
        outgoingCommunicationBeam = outgoingCommunicationBeam?.let(::fromBukkit),

        cams = cams.mapValues { fromBukkit(it.value) },
        vents = vents.map {
            VentGroupDTO(
                id = it.id,
                vents = it.vents.map(::fromBukkit)
            )
        },
        lightLevers = lightLevers.mapTo(mutableSetOf()) { fromBukkit(it.toBlockLocation()) },
        tasks = tasks.mapValuesTo(mutableMapOf()) { (_, locations) -> locations.mapTo(mutableSetOf()) { fromBukkit(it) } }
    )
}

@Suppress("DuplicatedCode")
fun GameAreaDTO.toGameArea(): GameArea {
    val area = GameArea(name, UUID.fromString(uuid))

    area.minCorner = minCorner?.toBukkit()
    area.maxCorner = maxCorner?.toBukkit()
    area.lobbySpawn = lobbySpawn?.toBukkit()
    area.gameSpawn = gameSpawn?.toBukkit()
    area.meetingRoomMin = meetingRoomMin?.toBukkit()
    area.meetingRoomMax = meetingRoomMax?.toBukkit()
    area.meetingBlock = meetingBlock?.toBukkit()
    area.ejectedFallPoint = ejectedFallPoint?.toBukkit()
    area.ejectedViewPoint = ejectedViewPoint?.toBukkit()
    area.cameraJoinPointMin = cameraJoinPointMin?.toBukkit()
    area.cameraJoinPointMax = cameraJoinPointMax?.toBukkit()
    area.lightPosMin = lightPosMin?.toBukkit()
    area.lightPosMax = lightPosMax?.toBukkit()
    area.seismicStabilizers1 = seismicStabilizers1?.toBukkit()
    area.seismicStabilizers2 = seismicStabilizers2?.toBukkit()
    area.seismicStabilizers1Particle = seismicStabilizers1Particle?.toBukkit()
    area.seismicStabilizers2Particle = seismicStabilizers2Particle?.toBukkit()
    area.communications = communications?.toBukkit()
    area.outgoingCommunicationBeam = outgoingCommunicationBeam?.toBukkit()

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
    area.tasks.putAll(tasks.mapValuesTo(mutableMapOf()) { entry -> entry.value.mapTo(mutableSetOf()) { it.toBukkit()} })

    return area
}
