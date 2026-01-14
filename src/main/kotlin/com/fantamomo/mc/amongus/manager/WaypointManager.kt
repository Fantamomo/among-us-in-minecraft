package com.fantamomo.mc.amongus.manager

import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import net.kyori.adventure.text.Component
import net.minecraft.core.Vec3i
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket.addWaypointPosition
import net.minecraft.resources.ResourceKey
import net.minecraft.world.waypoints.Waypoint.Icon
import net.minecraft.world.waypoints.WaypointStyleAssets
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.craftbukkit.entity.CraftPlayer
import java.util.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class WaypointManager(val game: Game) {
    private val waypointData: MutableSet<WaypointData> = mutableSetOf()

    private inner class WaypointData(
        val player: AmongUsPlayer
    ) {
        val actionBar = game.actionBarManager.createActionBarPart(
            player,
            "waypoint",
            ActionBarManager.ActionBarPartType.CENTER,
            100
        )

        val waypoints: MutableSet<Waypoint> = mutableSetOf()

        fun addWaypoint(waypoint: Waypoint) {
            if (waypoints.add(waypoint)) {
                sendPacket(waypoint.createAddPacket())
            }
        }

        private fun sendPacket(packet: ClientboundTrackedWaypointPacket) {
            val player = player.player ?: return
            val craftPlayer = (player as CraftPlayer)
            val handle = craftPlayer.handle
            val connection = handle.connection
            connection.send(packet)
        }

        fun removeWaypoint(waypoint: Waypoint) {
            if (waypoints.remove(waypoint)) {
                sendPacket(waypoint.createRemovePacket())
            }
        }

        fun removeAll() {
            waypoints.forEach {
                sendPacket(it.createRemovePacket())
            }
            waypoints.clear()
        }
    }

    class Waypoint(
        val translationKey: String,
        val color: Int,
        val vector: Vec3i
    ) {
        val uuid: UUID = UUID.randomUUID()
        val icon = createIcon(color)

        constructor(translationKey: String, color: Int, location: Location) : this(
            translationKey = translationKey,
            color = color,
            vector = Vec3i(location.blockX, location.blockY, location.blockZ)
        )

        constructor(translationKey: String, color: Color, location: Location) : this(
            translationKey = translationKey,
            color = color.asARGB(),
            location = location
        )

        fun createAddPacket(): ClientboundTrackedWaypointPacket =
            addWaypointPosition(uuid, icon, vector)

        fun createRemovePacket(): ClientboundTrackedWaypointPacket =
            ClientboundTrackedWaypointPacket.removeWaypoint(uuid)
    }

    fun assignWaypoint(player: AmongUsPlayer, waypoint: Waypoint) {
        val data = waypointData.find { it.player == player } ?: WaypointData(player).also(waypointData::add)
        data.addWaypoint(waypoint)
    }

    fun removeWaypoint(player: AmongUsPlayer, waypoint: Waypoint) {
        val data = waypointData.find { it.player == player } ?: return
        data.removeWaypoint(waypoint)
    }

    fun removePlayer(player: AmongUsPlayer) {
        val data = waypointData.find { it.player == player } ?: return
        data.removeAll()
        waypointData.remove(data)
    }

    private var ticks = 2

    fun tick() {
        if (ticks % 5 == 0) {
            for (data in waypointData) {
                val component = showActionbar(data)
                data.actionBar.component = component
            }
        }
        ticks++
    }

    private fun showActionbar(data: WaypointData): Component? {
        val amongUsPlayer = data.player
        val player = amongUsPlayer.player ?: return null
        if (amongUsPlayer.isInCams()) return null

        val eyeLoc = player.eyeLocation
        val playerYaw = normalizeYaw(eyeLoc.yaw)

        var bestWaypoint: Waypoint? = null
        var bestAngle = Double.MAX_VALUE
        var bestDistance = Double.MAX_VALUE

        for (waypoint in data.waypoints) {
            val dx = waypoint.vector.x + 0.5 - eyeLoc.x
            val dy = waypoint.vector.y + 0.5 - eyeLoc.y
            val dz = waypoint.vector.z + 0.5 - eyeLoc.z

            val distance = sqrt(dx * dx + dy * dy + dz * dz)

            val angleTo = Math.toDegrees(atan2(-dx, dz))
            val normalizedAngle = normalizeYaw(angleTo.toFloat())
            val diff = yawDifference(playerYaw, normalizedAngle)

            if (diff < bestAngle) {
                bestAngle = diff
                bestWaypoint = waypoint
                bestDistance = distance
            }
        }

        if (bestWaypoint == null) {
            return null
        }

        val allowedAngle = allowedAngleForDistance(bestDistance)

        if (bestAngle > allowedAngle) {
            return null
        }

        return Component.translatable(bestWaypoint.translationKey)

    }

    private fun allowedAngleForDistance(distance: Double): Double {
        val minDist = 5.0
        val maxDist = 50.0

        val maxAngle = 30.0
        val minAngle = 3.0

        val t = ((distance - minDist) / (maxDist - minDist))
            .coerceIn(0.0, 1.0)

        return maxAngle + (minAngle - maxAngle) * t
    }

    private fun normalizeYaw(yaw: Float): Double {
        var y = yaw.toDouble()
        while (y <= -180) y += 360
        while (y > 180) y -= 360
        return y
    }

    private fun yawDifference(a: Double, b: Double): Double {
        var diff = abs(a - b)
        if (diff > 180) diff = 360 - diff
        return diff
    }

    companion object {
        private val constructor = Icon::class.java.getDeclaredConstructor(
            ResourceKey::class.java,
            Optional::class.java,
        ).apply {
            isAccessible = true
        }

        private fun createIcon(color: Int): Icon = try {
            constructor.newInstance(WaypointStyleAssets.DEFAULT, Optional.of(color))
        } catch (_: Exception) {
            Icon.NULL
        }
    }
}