package com.fantamomo.mc.amongus.role.neutral

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.info.DeadReason
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.role.util.WinCheckPhase
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object ExecutionerRole : Role<ExecutionerRole, ExecutionerRole.AssignedExecutionerRole> {
    override val id: String = "executioner"
    override val team: Team = Team.NEUTRAL.EXECUTIONER
    override val defaultAbilities: Set<Ability<*, *>> = setOf()

    override fun assignTo(player: AmongUsPlayer) = AssignedExecutionerRole(player)

    class AssignedExecutionerRole(override val player: AmongUsPlayer) : AssignedRole<ExecutionerRole, AssignedExecutionerRole> {
        override val definition = ExecutionerRole
        override val winCheckPhase = WinCheckPhase.PRE

        var target: AmongUsPlayer? = null
            private set

        override fun onGameStart() {
            val players = player.game.players
            target = when (players.size) {
                1 -> null
                2 -> players.firstOrNull { it !== player && it.assignedRole?.definition !== JesterRole }
                else -> players.filter { it !== player && it.assignedRole?.definition !== JesterRole }.random()
            }

            target?.mannequinController?.setNameColorFor(player, NamedTextColor.GREEN)
        }

        override fun onGameEnd() {
            target?.mannequinController?.setNameColorFor(player, null)
            target = null
        }

        override fun hasWon() = player.isAlive && target?.deadReason === DeadReason.Ejected

        override fun scoreboardLine() = target?.let { target ->
            textComponent {
                translatable("role.executioner.scoreboard") {
                    args {
                        string("player", target.name)
                    }
                }
            }
        } ?: NO_TARGET

        companion object {
            private val NO_TARGET = Component.translatable("role.executioner.scoreboard.no_target")
        }
    }
}