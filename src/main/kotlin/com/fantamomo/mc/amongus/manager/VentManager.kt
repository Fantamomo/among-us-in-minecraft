package com.fantamomo.mc.amongus.manager

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.ability.abilities.VentAbility
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.util.getClosestLocationOnLine
import net.kyori.adventure.text.Component
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Player
import org.bukkit.util.Transformation
import java.util.*
import kotlin.uuid.Uuid


class VentManager(val game: Game) {
    val vents: List<Vent> = game.area.vents.flatMap { group -> group.vents.map { Vent(group.id, it) } }
    val ventsById: Map<Uuid, Vent> = vents.associateBy { it.uuid }
    val groups = vents.groupBy { it.groupId }.map { VentGroup(it.key, it.value) }
    private val ventedPlayers = mutableMapOf<AmongUsPlayer, VentedPlayer>()

    inner class VentedPlayer(
        val player: AmongUsPlayer,
        vent: Vent
    ) {
        private var invalid: Boolean = false
        val displays: Map<Vent, BlockDisplay> = vent.group.vents.associateWith(::createDisplay)
        var vent: Vent = vent
            set(value) {
                checkIsValid()
                if (field == value) return
                val player = player.player

                val hiddenDisplay = getDisplay(field)
                player?.showEntity(AmongUs, hiddenDisplay)
                if (game.settings[SettingsKey.VENT_VISIBLY_AS_WAYPOINT]) {
                    game.waypointManager.assignWaypoint(this.player, field.waypoint)
                }

                val visibleDisplay = getDisplay(value)
                player?.hideEntity(AmongUs, visibleDisplay)
                game.waypointManager.removeWaypoint(this.player, value.waypoint)

                if (player != null) {
                    val location = value.normalizedLocation.withRotation(player)
                    player.teleport(location)
                    orientationChange(this.player, location)
                }

                field = value
                _otherVents = null
                _otherVentByLocation = null
            }
        private var _otherVents: List<Vent>? = null
        val otherVents: List<Vent>
            get() {
                checkIsValid()
                return _otherVents ?: vent.otherVents.also { _otherVents = it }
            }
        private var _otherVentByLocation: Map<Location, Uuid>? = null
        internal val otherVentByLocation: Map<Location, Uuid>
            get() {
                checkIsValid()
                return _otherVentByLocation ?: otherVents.associateWith { it.uuid }
                    .mapKeys { it.key.normalizedLocation }.also { _otherVentByLocation = it }
            }

        val actionBar = game.actionBarManager.createActionBarPart(
            player,
            "vent",
            ActionBarManager.ActionBarPartType.CENTER,
            200
        ).apply {
            component = Component.translatable("actionbar.vent.next")
            visible = false
        }

        init {
            val player = player.player
            if (player != null) {
                val location = vent.normalizedLocation.withRotation(player)
                player.teleport(location)
                orientationChange(this.player, location)
                val ventsAsWaypoints = game.settings[SettingsKey.VENT_VISIBLY_AS_WAYPOINT]
                for (entry in displays) {
                    if (vent == entry.key) continue
                    player.showEntity(AmongUs, entry.value)
                    if (ventsAsWaypoints) {
                        game.waypointManager.assignWaypoint(this.player, entry.key.waypoint)
                    }
                }
            }
        }

        private var lastActive: Vent? = null

        fun setActive(vent: Vent?) {
            if (vent == lastActive) return
            lastActive = vent
            for (entry in displays) {
                val (v, display) = entry
                if (v == this.vent) continue
                val current = display.glowColorOverride
                val new = if (v == vent) Color.YELLOW else Color.RED
                if (current != new) {
                    display.glowColorOverride = new
                }
            }
            actionBar.visible = vent != null
        }

        private fun checkIsValid() {
            if (invalid) throw IllegalStateException("VentedPlayer is invalid")
        }

        fun getDisplay(vent: Vent) = displays[vent] ?: throw IllegalArgumentException("No display for $vent")

        private fun createDisplay(vent: Vent): BlockDisplay {
            val location = vent.location
            return location.world.spawn(location.clone(), BlockDisplay::class.java) { display ->
                display.block = Material.OAK_LEAVES.createBlockData()
                display.isGlowing = true
                display.glowColorOverride = Color.RED
                display.isVisibleByDefault = false
            }.apply(EntityManager::addEntityToRemoveOnStop)
        }

        fun dispose() {
            checkIsValid()
            invalid = true

            _otherVents = null
            _otherVentByLocation = null

            displays.forEach { (_, display) ->
                display.remove()
            }
            vent.group.vents.forEach { vent ->
                game.waypointManager.removeWaypoint(player, vent.waypoint)
            }
            game.actionBarManager.removeBarPart(actionBar)
        }
    }

    inner class VentGroup internal constructor(val groupId: Int, val vents: List<Vent>)

    inner class Vent internal constructor(val groupId: Int, val location: Location) {
        val uuid = Uuid.random()
        val group: VentGroup by lazy { ventGroup(groupId) }
        val otherVents: List<Vent>
            get() {
                val vents = group.vents
                return vents.toMutableList().apply { remove(this@Vent) }
            }
        val normalizedLocation = location.clone().add(0.5, 0.0, 0.5)
        val displayEntity = location.world.spawn(location, BlockDisplay::class.java) { display ->
            display.block = Material.STONE.createBlockData()
            display.isGlowing = true
            display.glowColorOverride = Color.RED
            display.isVisibleByDefault = false
            display.transformation = display.transformation.run {
                Transformation(
                    translation,
                    leftRotation,
                    scale.sub(0.0f, 1.0f, 0.0f),
                    rightRotation
                )
            }
        }.apply(EntityManager::addEntityToRemoveOnStop)
        val displayEntityVisible: MutableSet<UUID> = mutableSetOf()
        val waypoint = WaypointManager.Waypoint("actionbar.vent.next", Color.BLUE, location)
    }

    private fun ventGroup(id: Int) = groups.first { it.groupId == id }

    fun isVented(player: AmongUsPlayer): Boolean = player in ventedPlayers

    fun getCurrentVent(player: AmongUsPlayer): Vent? = ventedPlayers[player]?.vent

    fun ventIn(amongUsPlayer: AmongUsPlayer) {
        if (isVented(amongUsPlayer)) return
        val vent = nearestVent(amongUsPlayer) ?: throw IllegalStateException("No nearest vent")
        ventIn(amongUsPlayer, vent)
    }

    fun ventIn(amongUsPlayer: AmongUsPlayer, vent: Vent) {
        if (isVented(amongUsPlayer)) return
        ventedPlayers[amongUsPlayer] = VentedPlayer(amongUsPlayer, vent)
    }

    fun ventOut(amongUsPlayer: AmongUsPlayer) {
        if (!isVented(amongUsPlayer)) return
        val ventedPlayer = ventedPlayers.remove(amongUsPlayer) ?: return
        ventedPlayer.dispose()
    }

    /**
     * Finds the nearest vent to a given location within a specified maximum distance.
     *
     * @param location The location from which the nearest vent is determined.
     * @param maxDistance The maximum allowable squared distance to consider; defaults to Double.MAX_VALUE.
     * @return The nearest vent within the specified maximum distance, or null if no vent is found.
     */
    fun nearestVent(location: Location, maxDistance: Double = Double.MAX_VALUE): Vent? {
        var nearestVent: Vent? = null
        var nearestDistance = maxDistance
        for (vent in vents) {
            val distance = location.distanceSquared(vent.normalizedLocation)
            if (distance > nearestDistance) continue
            nearestVent = vent
            nearestDistance = distance
        }
        return nearestVent
    }

    fun nearestVent(player: AmongUsPlayer, maxDistance: Double = Double.MAX_VALUE): Vent? =
        nearestVent(player.player?.location ?: return null, maxDistance)

    fun isNearVent(amongUsPlayer: AmongUsPlayer): Boolean {
        val location = amongUsPlayer.player?.location ?: return false
        val maxDistance = game.settings[SettingsKey.VENT_DISTANCE].distance
        return vents.any { it.normalizedLocation.distanceSquared(location) < maxDistance * maxDistance }
    }

    fun doVent(player: AmongUsPlayer) {
        if (isVented(player)) {
            ventOut(player)
        } else {
            ventIn(player)
        }
    }

    fun changeVent(amongUsPlayer: AmongUsPlayer, to: Vent) {
        val ventedPlayer = ventedPlayers[amongUsPlayer] ?: return
        val currentVent = ventedPlayer.vent
        if (currentVent.group != to.group) {
            changeVentAndGroup(amongUsPlayer, to)
            return
        }
        ventedPlayer.vent = to
    }

    private fun Location.withRotation(player: Player) = clone().setRotation(player.yaw, player.pitch)

    private fun changeVentAndGroup(player: AmongUsPlayer, to: Vent) {
        ventOut(player)
        ventIn(player, to)
    }

    fun nextVent(player: AmongUsPlayer) {
        val location: Location = player.player?.location ?: return
        val ventedPlayer = ventedPlayers[player] ?: return
        val otherVents = ventedPlayer.otherVentByLocation
        val targetUuid = getClosestLocationOnLine(location, otherVents, 2.0, 0.1) ?: return
        val targetVent = ventsById[targetUuid] ?: return
        changeVent(player, targetVent)
    }

    fun orientationChange(player: AmongUsPlayer, to: Location) {
        val ventedPlayer = ventedPlayers[player] ?: return
        val otherVents = ventedPlayer.otherVentByLocation
        val targetUuid = getClosestLocationOnLine(to, otherVents, 2.0, 0.1)
        if (targetUuid == null) {
            ventedPlayer.setActive(null)
            return
        }
        val targetVent = ventsById[targetUuid] ?: return
        ventedPlayer.setActive(targetVent)
    }

    private var tickCounter = 0
    fun tick() {
        // if (game.phase != GamePhase.RUNNING) return // todo: uncommit it
        tickCounter++
        if (tickCounter % 2 != 0) return
        for (amongUsPlayer in game.players) {
            val player = amongUsPlayer.player ?: continue
            if (amongUsPlayer.hasAbility(VentAbility)) {
                val nearestVent = if (isVented(amongUsPlayer)) null else nearestVent(amongUsPlayer, 10.0 * 10.0)
                for (otherVent in vents) {
                    if (otherVent == nearestVent) continue
                    if (!otherVent.displayEntityVisible.remove(amongUsPlayer.uuid)) continue
                    player.hideEntity(AmongUs, otherVent.displayEntity)
                    break
                }
                if (nearestVent == null) continue
                if (!nearestVent.displayEntityVisible.add(amongUsPlayer.uuid)) continue
                val entity = nearestVent.displayEntity
                player.showEntity(AmongUs, entity)
            }
        }
    }

    internal fun removePlayer0(player: AmongUsPlayer) {
        ventedPlayers.remove(player)?.dispose()
    }
}