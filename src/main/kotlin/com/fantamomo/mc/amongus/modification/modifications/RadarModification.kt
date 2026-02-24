package com.fantamomo.mc.amongus.modification.modifications

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.manager.ActionBarManager
import com.fantamomo.mc.amongus.manager.waypoint.MutableWaypointPosProvider
import com.fantamomo.mc.amongus.manager.waypoint.WaypointManager
import com.fantamomo.mc.amongus.modification.AssignedModification
import com.fantamomo.mc.amongus.modification.Modification
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import net.kyori.adventure.text.Component
import org.bukkit.Color
import kotlin.math.roundToInt
import kotlin.math.sqrt

object RadarModification : Modification<RadarModification, RadarModification.AssignedRadarModification> {
    override val id: String = "radar"

    override fun assignTo(player: AmongUsPlayer) = AssignedRadarModification(player)

    class AssignedRadarModification(override val player: AmongUsPlayer) :
        AssignedModification<RadarModification, AssignedRadarModification> {
        override val definition = RadarModification

        val mutableLocation = MutableWaypointPosProvider(player.livingEntity.location)
        private var lastDistanceSquared = 0.0
        private var lastDistanceDisplay = 0
        private var shown = false

        val waypoint = WaypointManager.Waypoint(
            Component.empty(),
            Color.BLUE,
            mutableLocation
        )
        val actionBar = player.game.actionBarManager.part(
            player,
            "radar",
            ActionBarManager.ActionBarPartType.LEFT,
            100
        )

        init {
            waypoint.showDisplay = false
        }

        override fun onStart() {
            player.game.waypointManager.assignWaypoint(player, waypoint)
            shown = true
        }

        override fun onTick() {
            if (!shown) return
            val thisLoc = (player.mannequinController.getEntity() ?: player.livingEntity).location
            var nearestDistance = Double.MAX_VALUE
            var nearestLoc = thisLoc
            for (player in player.game.players) {
                if (player === this.player) continue
                val targetLocation = (player.mannequinController.getEntity() ?: player.livingEntity).location
                val d = thisLoc.distanceSquared(targetLocation)
                if (d < nearestDistance) {
                    nearestDistance = d
                    nearestLoc = targetLocation
                }
            }
            if (nearestDistance == Double.MAX_VALUE) {
                waypoint.isVisible = false
                return
            }
            if (nearestDistance == lastDistanceSquared) return
            lastDistanceSquared = nearestDistance
            val roundToInt = nearestDistance.roundToInt()
            if (roundToInt != lastDistanceDisplay) {
                lastDistanceDisplay = roundToInt
                val distanceToDisplay = if (nearestDistance > 25 * 25) ">25" else sqrt(nearestDistance).roundToInt().toString()
                actionBar.componentLike = textComponent {
                    translatable("modification.radar.display") {
                        args {
                            string("distance", distanceToDisplay)
                        }
                    }
                }
            }
            mutableLocation.location = nearestLoc
        }

        override fun onEnd() {
            player.game.waypointManager.removeWaypoint(player, waypoint)
            shown = false
        }
    }
}