package com.fantamomo.mc.amongus.role.neutral

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.languages.component
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.manager.MeetingManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.bot.mangement.BotVoteTargetController
import com.fantamomo.mc.amongus.player.botOrNull
import com.fantamomo.mc.amongus.player.info.DeadReason
import com.fantamomo.mc.amongus.player.isAlive
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.SupportBotsRole
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.role.util.WinCheckPhase
import com.fantamomo.mc.amongus.util.log.elements.CustomRoleActionElements
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object ExecutionerRole : Role<ExecutionerRole, ExecutionerRole.AssignedExecutionerRole> {
    override val id: String = "executioner"
    override val team: Team = Team.NEUTRAL.EXECUTIONER
    override val defaultAbilities: Set<Ability<*, *>> = setOf()

    override fun assignTo(player: AmongUsPlayer) = AssignedExecutionerRole(player)

    class AssignedExecutionerRole(override val player: AmongUsPlayer) : AssignedRole<ExecutionerRole, AssignedExecutionerRole>, SupportBotsRole {
        override val definition = ExecutionerRole
        override val winCheckPhase = WinCheckPhase.PRE

        var target: AmongUsPlayer? = null
            private set

        override fun onGameStart() {
            val players = player.game.players
            target = when (players.size) {
                1 -> null
                2 -> players.firstOrNull { it !== player && it.role.definition !== JesterRole }
                else -> players.filter { it !== player && it.role.definition !== JesterRole }.random()
            }

            target?.mannequinController?.setNameColorFor(player, NamedTextColor.GREEN)
            player.game.actionLog.add(CustomRoleActionElements.ExecutionerTargetSelected(player.uuid, target?.uuid))
        }

        override fun onGameEnd() {
            target?.mannequinController?.setNameColorFor(player, null)
        }

        override fun hasWon() = player.isAlive() && target?.deadReason === DeadReason.Ejected

        override fun scoreboardLine() = target?.let { target ->
            textComponent {
                translatable("role.executioner.scoreboard") {
                    args {
                        string("player", target.name)
                    }
                }
            }
        } ?: NO_TARGET

        override fun gameEndInfo() = textComponent {
            translatable("role.executioner.end.target") {
                args {
                    val target = target
                    if (target != null) {
                        string("player", target.name)
                    } else {
                        component("player", NO_TARGET)
                    }
                }
            }
        }

        override fun createBotVoteTargetController() = ExecutionerBotVoteTargetController(this)

        companion object {
            private val NO_TARGET = Component.translatable("role.executioner.scoreboard.no_target")
        }
    }

    class ExecutionerBotVoteTargetController(val role: AssignedExecutionerRole) : BotVoteTargetController(
        role.player.botOrNull
            ?: throw IllegalArgumentException("Player must be a bot to create SnitchBotVoteTargetController")
    ) {
        private val random = default(role.player)

        override fun executesAtTheStartOfTheMeeting() = false

        override fun getTarget(availableTargets: List<AmongUsPlayer>): Target? {
            val target = role.target ?: return random.getTarget(availableTargets)
            if (target !in availableTargets) return random.getTarget(availableTargets)
            if (availableTargets.isEmpty()) return Target.Skip
            val votes = bot.game.meetingManager.meeting?.votes ?: return Target.Skip
            val voteCounts = availableTargets.associateWith { player ->
                votes.count { (it.value as? MeetingManager.Vote.For)?.target === player }
            }
            val targetVotes = voteCounts[target]
            if (targetVotes != null && (targetVotes >= 1 || availableTargets.size <= 4)) return Target.Player(target)
            return random.getTarget(availableTargets)
        }
    }
}