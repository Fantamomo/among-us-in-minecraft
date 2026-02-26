package com.fantamomo.mc.amongus.role.neutral

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.abilities.EatBodyAbility
import com.fantamomo.mc.amongus.languages.numeric
import com.fantamomo.mc.amongus.manager.waypoint.MutableWaypointPosProvider
import com.fantamomo.mc.amongus.manager.waypoint.WaypointManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.settings.SettingsKey
import net.kyori.adventure.text.Component
import org.bukkit.Color

object CannibalRole : Role<CannibalRole, CannibalRole.AssignedCannibalRole> {
    override val id: String = "cannibal"
    override val team: Team = Team.NEUTRAL.CANNIBAL

    override val defaultAbilities: Set<Ability<*, *>> = setOf(
        EatBodyAbility
    )

    override fun assignTo(player: AmongUsPlayer) = AssignedCannibalRole(player)

    class AssignedCannibalRole(override val player: AmongUsPlayer) : AssignedRole<CannibalRole, AssignedCannibalRole> {
        override val definition = CannibalRole

        val bodiesToEat: Int
            get() = player.game.settings[SettingsKey.ROLES.CANNIBAL.BODIES_TO_EAT]
        var eatenBodies: Int = 0
            private set
        private var ticks = -1
        private var lastSeenCorpse = false

        val mutableLocation = MutableWaypointPosProvider(player.livingEntity.location)
        val waypoint = WaypointManager.Waypoint(
            Component.empty(),
            Color.MAROON,
            mutableLocation
        )

        init {
            waypoint.isVisible = false
            waypoint.showDisplay = false
        }

        override fun onGameStart() {
            player.game.waypointManager.assignWaypoint(player, waypoint)
            ticks = 0
        }

        override fun tick() {
            if (ticks <= -1) return
            ticks++
            if (ticks % 20 != 0) return
            val nearestCorpse = player.game.killManager.nearestCorpse(player.livingEntity.location)
            if (lastSeenCorpse != (nearestCorpse != null)) {
                lastSeenCorpse = nearestCorpse != null
                waypoint.isVisible = lastSeenCorpse
            }
            if (nearestCorpse != null) {
                mutableLocation.location = nearestCorpse.mannequin.location
            }
        }

        override fun onGameEnd() {
            ticks = -1
            player.game.waypointManager.removeWaypoint(player, waypoint)
        }

        fun incrementEatenBodies() {
            eatenBodies++
            player.statistics.cannibalEatenBodies.increment()
        }

        override val description: Component
            get() = textComponent {
                translatable("role.cannibal.description.in_game") {
                    args {
                        numeric("count", bodiesToEat)
                    }
                }
            }

        override fun scoreboardLine() = textComponent {
            translatable("role.cannibal.scoreboard") {
                args {
                    numeric("count", eatenBodies)
                }
            }
        }

        override fun hasWon() = eatenBodies >= bodiesToEat
    }
}