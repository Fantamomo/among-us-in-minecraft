package com.fantamomo.mc.amongus.area

import org.bukkit.Location
import org.bukkit.World
import java.util.*
import kotlin.reflect.KMutableProperty1

data class GameArea(
    val name: String,
    val uuid: UUID
) {
    var minCorner: Location? = null
    var maxCorner: Location? = null

    var lobbySpawn: Location? = null
    var gameSpawn: Location? = null
    var wardrobe: Location? = null

    var meetingRoomMin: Location? = null
    var meetingRoomMax: Location? = null
    var meetingBlock: Location? = null
        set(value) {
            field = value?.toBlockLocation()
        }
    var ejectedFallPoint: Location? = null
    var ejectedViewPoint: Location? = null

    var cameraJoinPointMin: Location? = null
        set(value) {
            field = value?.toBlockLocation()
        }
    var cameraJoinPointMax: Location? = null
        set(value) {
            field = value?.toBlockLocation()
        }
    var lightPosMin: Location? = null
    var lightPosMax: Location? = null
    var seismicStabilizers1: Location? = null
        set(value) {
            field = value?.toBlockLocation()
        }
    var seismicStabilizers2: Location? = null
        set(value) {
            field = value?.toBlockLocation()
        }
    var seismicStabilizers1Particle: Location? = null
        set(value) {
            field = value?.toCenterLocation()
        }
    var seismicStabilizers2Particle: Location? = null
        set(value) {
            field = value?.toCenterLocation()
        }
    var communications: Location? = null
        set(value) {
            field = value?.toBlockLocation()
        }
    var outgoingCommunicationBeam: Location? = null

    var cams: MutableMap<String, Location> = mutableMapOf()
        private set
    var vents: MutableList<VentGroup> = mutableListOf()
        private set
    var lightLevers: MutableList<Location> = mutableListOf()
        private set
    var tasks: MutableMap<String, MutableSet<Location>> = mutableMapOf()

    fun isValid(): Boolean {
        minCorner ?: return false
        maxCorner ?: return false
        lobbySpawn ?: return false
        gameSpawn ?: return false
        meetingBlock ?: return false
        ejectedFallPoint ?: return false
        ejectedViewPoint ?: return false
        return !(cameraJoinPointMin == null && cameraJoinPointMax == null)
    }

    fun getMissedLocations(): List<String> {
        if (isValid()) return listOf()
        val missed = mutableListOf<String>()
        if (minCorner == null) missed.add("min_corner")
        if (maxCorner == null) missed.add("max_corner")
        if (lobbySpawn == null) missed.add("lobby_spawn")
        if (gameSpawn == null) missed.add("game_spawn")
        if (meetingBlock == null) missed.add("meeting_block")
        if (ejectedFallPoint == null) missed.add("ejected_fall_point")
        if (ejectedViewPoint == null) missed.add("ejected_view_point")
        if (cameraJoinPointMin == null || cameraJoinPointMax == null) {
            if (cameraJoinPointMin == null) missed.add("camera_join_point_min")
            else missed.add("camera_join_point_max")
        }
        return missed
    }

    @Suppress("DuplicatedCode")
    fun withWorld(world: World): GameArea {
        val clone = this.copy()
        clone.minCorner = minCorner?.withWorld(world)
        clone.maxCorner = maxCorner?.withWorld(world)
        clone.lobbySpawn = lobbySpawn?.withWorld(world)
        clone.gameSpawn = gameSpawn?.withWorld(world)
        clone.wardrobe = wardrobe?.withWorld(world)
        clone.meetingRoomMin = meetingRoomMin?.withWorld(world)
        clone.meetingRoomMax = meetingRoomMax?.withWorld(world)
        clone.meetingBlock = meetingBlock?.withWorld(world)
        clone.ejectedFallPoint = ejectedFallPoint?.withWorld(world)
        clone.ejectedViewPoint = ejectedViewPoint?.withWorld(world)
        clone.cameraJoinPointMin = cameraJoinPointMin?.withWorld(world)
        clone.cameraJoinPointMax = cameraJoinPointMax?.withWorld(world)
        clone.lightPosMin = lightPosMin?.withWorld(world)
        clone.lightPosMax = lightPosMax?.withWorld(world)
        clone.seismicStabilizers1 = seismicStabilizers1?.withWorld(world)
        clone.seismicStabilizers2 = seismicStabilizers2?.withWorld(world)
        clone.seismicStabilizers1Particle = seismicStabilizers1Particle?.withWorld(world)
        clone.seismicStabilizers2Particle = seismicStabilizers2Particle?.withWorld(world)
        clone.communications = communications?.withWorld(world)
        clone.outgoingCommunicationBeam = outgoingCommunicationBeam?.withWorld(world)

        cams.forEach { clone.cams[it.key] = it.value.withWorld(world) }
        vents.forEach { clone.vents.add(it.withWorld(world)) }
        lightLevers.forEach { clone.lightLevers.add(it.withWorld(world)) }
        tasks.forEach { entry -> clone.tasks[entry.key] = entry.value.mapTo(mutableSetOf()) { it.withWorld(world) } }
        return clone
    }

    private fun Location.withWorld(world: World) = this.clone().apply { this.world = world }

    companion object {
        val properties: Map<String, KMutableProperty1<GameArea, Location?>> = mapOf(
            "minCorner" to GameArea::minCorner,
            "maxCorner" to GameArea::maxCorner,
            "lobbySpawn" to GameArea::lobbySpawn,
            "gameSpawn" to GameArea::gameSpawn,
            "wardrobe" to GameArea::wardrobe,
            "meetingRoomMin" to GameArea::meetingRoomMin,
            "meetingRoomMax" to GameArea::meetingRoomMax,
            "meetingBlock" to GameArea::meetingBlock,
            "ejectedFallPoint" to GameArea::ejectedFallPoint,
            "ejectedViewPoint" to GameArea::ejectedViewPoint,
            "cameraJoinPointMin" to GameArea::cameraJoinPointMin,
            "cameraJoinPointMax" to GameArea::cameraJoinPointMax,
            "seismicStabilizers1" to GameArea::seismicStabilizers1,
            "seismicStabilizers2" to GameArea::seismicStabilizers2,
            "seismicStabilizers1Particle" to GameArea::seismicStabilizers1Particle,
            "seismicStabilizers2Particle" to GameArea::seismicStabilizers2Particle,
            "lightPosMin" to GameArea::lightPosMin,
            "lightPosMax" to GameArea::lightPosMax,
            "communications" to GameArea::communications,
            "outgoingCommunicationBeam" to GameArea::outgoingCommunicationBeam
        )
    }
}