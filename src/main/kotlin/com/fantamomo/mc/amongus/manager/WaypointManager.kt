package com.fantamomo.mc.amongus.manager

import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.util.internal.NMS
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
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

class WaypointManager(val game: Game) {
    private val waypointData: MutableSet<WaypointData> = mutableSetOf()

    private inner class WaypointData(
        val player: AmongUsPlayer
    ) {
        val actionBar = game.actionBarManager.part(
            player,
            "waypoint",
            ActionBarManager.ActionBarPartType.CENTER,
            100,
            { showActionbar(this) }
        )

        val waypoints: MutableSet<Waypoint> = mutableSetOf()

        fun addWaypoint(waypoint: Waypoint) {
            if (waypoints.add(waypoint)) {
                sendPacket(waypoint.createAddPacket())
            }
        }

        // we need to use packets directly, due that paper currently does not provide an api for it
        @NMS
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

        fun maySendUpdate() {
            for (waypoint in waypoints) {
                if (waypoint.dirty) {
                    updateWaypoint(waypoint)
                }
            }
        }

        private fun updateWaypoint(waypoint: Waypoint, playerRejoined: Boolean = false) {
            val visible = waypoint.isVisible
            if (playerRejoined) {
                if (visible) sendPacket(waypoint.createAddPacket())
                waypoint.lastVisible = visible
                return
            }
            if (visible != waypoint.lastVisible) {
                if (visible) sendPacket(waypoint.createAddPacket())
                else sendPacket(waypoint.createRemovePacket())
                waypoint.lastVisible = visible
            }
            if (!visible) return
            sendPacket(waypoint.createUpdatePacket())
            waypoint.dirty = false
        }

        fun resendAll() {
            waypoints.forEach { updateWaypoint(it, true) }
        }
    }

    class Waypoint(
        val translationKey: String,
        val color: Int,
        vector: Vec3i
    ) {
        internal var lastVisible: Boolean = true
        var isVisible: Boolean = true
            set(value) {
                field = value
                setDirty()
            }
        val uuid: UUID = UUID.randomUUID()
        val icon = createIcon(color)
        var dirty: Boolean = false
        var vector: Vec3i = vector
            set(value) {
                field = value
                setDirty()
            }

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

        fun setDirty() {
            dirty = true
        }

        fun setLocation(location: Location) {
            vector = Vec3i(location.blockX, location.blockY, location.blockZ)
        }

        @NMS
        fun createAddPacket(): ClientboundTrackedWaypointPacket =
            addWaypointPosition(uuid, icon, vector)

        @NMS
        fun createRemovePacket(): ClientboundTrackedWaypointPacket =
            ClientboundTrackedWaypointPacket.removeWaypoint(uuid)

        @NMS
        fun createUpdatePacket(): ClientboundTrackedWaypointPacket =
            ClientboundTrackedWaypointPacket.updateWaypointPosition(uuid, icon, vector)
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

    fun onPlayerRejoin(player: AmongUsPlayer) {
        val data = waypointData.find { it.player == player } ?: return
        data.resendAll()
    }

    fun tick() {
        waypointData.forEach { data ->
            data.maySendUpdate()
        }
    }

    /**
     * Displays an action bar with details about the most relevant visible waypoint
     * near the given player's current position and orientation, if any are applicable.
     *
     * @param data The waypoint data encapsulating the player and their associated waypoints.
     *             This includes information about each waypoint's visibility and position
     *             relative to the player.
     * @return A `Component` representing the text to be displayed in the action bar
     *         regarding the nearest relevant waypoint, or `null` if no suitable waypoint is found
     *         or conditions to show the action bar are not met.
     */
    private fun showActionbar(data: WaypointData): Component? {
        if (data.waypoints.isEmpty()) return null
        if (data.waypoints.all { !it.isVisible }) return null
        val amongUsPlayer = data.player
        val player = amongUsPlayer.player ?: return null
        if (amongUsPlayer.isInCams()) return null

        val eyeLoc = player.eyeLocation
        val px = eyeLoc.x
        val py = eyeLoc.y
        val pz = eyeLoc.z

        val playerYaw = normalizeYaw(eyeLoc.yaw)

        var bestWaypoint: Waypoint? = null
        var bestAngle = Double.MAX_VALUE
        var bestDistSq = Double.MAX_VALUE

        for (waypoint in data.waypoints) {
            if (!waypoint.isVisible) continue

            val v = waypoint.vector
            val dx = v.x + 0.5 - px
            val dy = v.y + 0.5 - py
            val dz = v.z + 0.5 - pz

            val distSq = dx * dx + dy * dy + dz * dz
            val angleTo = Math.toDegrees(kotlin.math.atan2(-dx, dz))
            val diff = yawDifference(playerYaw, angleTo)

            if (diff < bestAngle - 0.5 ||
                (abs(diff - bestAngle) < 0.5 && distSq < bestDistSq)
            ) {
                bestAngle = diff
                bestWaypoint = waypoint
                bestDistSq = distSq
            }
        }

        val waypoint = bestWaypoint ?: return null

        val distance = kotlin.math.sqrt(bestDistSq)
        val allowedAngle = allowedAngleForDistance(distance)

        if (bestAngle > allowedAngle) return null

        return Component.translatable(waypoint.translationKey).color(TextColor.color(waypoint.color))
    }

    private fun allowedAngleForDistance(distance: Double): Double {
        return when {
            distance < 10 -> 45.0
            distance < 30 -> 30.0
            distance < 60 -> 20.0
            else -> 15.0
        }
    }

    private fun normalizeYaw(yaw: Float): Double {
        val y = yaw.toDouble()
        return ((y + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
    }

    private fun yawDifference(a: Double, b: Double): Double {
        val diff = abs(a - b)
        return if (diff > 180.0) 360.0 - diff else diff
    }

    fun end() {
        waypointData.forEach { it.removeAll() }
        waypointData.clear()
    }

    companion object {
        private val constructor = Icon::class.java.getDeclaredConstructor(
            ResourceKey::class.java,
            Optional::class.java,
        ).apply {
            isAccessible = true
        }

        /**
         * Creates an `Icon` instance with the specified color.
         *
         * @param color The color to be applied to the icon.
         * @return The newly created `Icon` instance, or `Icon.NULL` if the creation fails.
         */
        private fun createIcon(color: Int): Icon = try {
            constructor.newInstance(WaypointStyleAssets.DEFAULT, Optional.of(color))
        } catch (_: Exception) {
            Icon.NULL
        }
    }
}