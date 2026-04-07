package com.fantamomo.mc.amongus.role.neutral

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.newLine
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.abilities.EatBodyAbility
import com.fantamomo.mc.amongus.languages.numeric
import com.fantamomo.mc.amongus.manager.waypoint.MutableWaypointPosProvider
import com.fantamomo.mc.amongus.manager.waypoint.WaypointManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.humanOrNull
import com.fantamomo.mc.amongus.player.isBot
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.SupportBotsRole
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.util.TickContext
import net.kyori.adventure.text.Component
import org.bukkit.Color

object CannibalRole : Role<CannibalRole, CannibalRole.AssignedCannibalRole> {
    override val id: String = "cannibal"
    override val team: Team = Team.NEUTRAL.CANNIBAL

    override val defaultAbilities: Set<Ability<*, *>> = setOf(
        EatBodyAbility
    )

    override fun assignTo(player: AmongUsPlayer) = AssignedCannibalRole(player)

    class AssignedCannibalRole(override val player: AmongUsPlayer) : AssignedRole<CannibalRole, AssignedCannibalRole>, SupportBotsRole {
        override val definition = CannibalRole

        val bodiesToEat: Int
            get() = player.game.settings[SettingsKey.ROLES.CANNIBAL.BODIES_TO_EAT]
        var eatenBodies: Int = 0
            private set
        private var doTick = false
        private var lastSeenCorpse = false

        val mutableLocation = MutableWaypointPosProvider(player.location)
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
            if (player.isBot) return
            player.game.waypointManager.assignWaypoint(player, waypoint)
            doTick = true
        }

        override fun tick(tickContext: TickContext) {
            if (!doTick) return
            if (player.isBot) return
            if (tickContext.isBy(20)) return
            val nearestCorpse = player.game.killManager.nearestCorpse(player.location)
            if (lastSeenCorpse != (nearestCorpse != null)) {
                lastSeenCorpse = nearestCorpse != null
                waypoint.isVisible = lastSeenCorpse
            }
            if (nearestCorpse != null) {
                mutableLocation.location = nearestCorpse.mannequin.location
            }
        }

        override fun onGameEnd() {
            doTick = false
            if (player.isBot) return
            player.game.waypointManager.removeWaypoint(player, waypoint)
        }

        fun incrementEatenBodies() {
            eatenBodies++
            player.humanOrNull?.statistics?.cannibalEatenBodies?.increment()
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

        override fun gameEndInfo() = textComponent {
            translatable("role.cannibal.end.eaten_bodies") {
                args {
                    numeric("count", eatenBodies)
                }
            }
            newLine()
            translatable("role.cannibal.end.remaining") {
                args {
                    numeric("count", bodiesToEat - eatenBodies)
                }
            }
        }
    }
}