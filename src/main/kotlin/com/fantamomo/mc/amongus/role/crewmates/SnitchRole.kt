package com.fantamomo.mc.amongus.role.crewmates

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.languages.numeric
import com.fantamomo.mc.amongus.player.*
import com.fantamomo.mc.amongus.player.bot.mangement.BotVoteTargetController
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.SupportBotsRole
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.util.TickContext
import com.fantamomo.mc.amongus.util.log.elements.CustomRoleActionElements
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.TitlePart

object SnitchRole : Role<SnitchRole, SnitchRole.AssignedSnitchRole> {
    override val id: String = "snitch"
    override val team: Team = Team.CREWMATES
    override val defaultAbilities: Set<Ability<*, *>> = setOf()

    override fun assignTo(player: AmongUsPlayer) = AssignedSnitchRole(player)

    class AssignedSnitchRole(override val player: AmongUsPlayer) : AssignedRole<SnitchRole, AssignedSnitchRole>,
        SupportBotsRole {
        override val definition = SnitchRole

        private var lastCanSeeImposters = false
        private var sendWarning = false

        fun taskLeft(): Int = player.tasks.count { !it.completed }

        fun canSeeImposters(): Boolean = player.tasks.all { it.completed }

        override fun scoreboardLine(): Component? {
            if (player.game.phase == GamePhase.REVEALING_ROLES) return SCOREBOARD_LINE_WAITING
            if (!player.isAlive()) return null
            return when (val left = taskLeft()) {
                0 -> SCOREBOARD_LINE_FINISHED
                1 -> SCOREBOARD_LINE_LEFT
                else -> textComponent {
                    translatable("role.snitch.scoreboard.tasks_left") {
                        args {
                            numeric("count", left)
                        }
                    }
                }
            }
        }

        override fun tick(tickContext: TickContext) {
            if (player.game.phase == GamePhase.REVEALING_ROLES) return
            if (!player.isAlive()) return
            if (taskLeft() <= 1) {
                if (!sendWarning) {
                    sendWarning = true
                    player.game.actionLog.add(CustomRoleActionElements.SnitchOneTaskLeft(player.uuid))
                    for (player in player.game.players) {
                        if (player.isBot) continue
                        if (player.role.definition.team == Team.IMPOSTERS) {
                            val viewer = player.player
                            viewer?.sendTitlePart(TitlePart.TITLE, WARNING)
                            if (viewer != null) {
                                this.player.mannequinController.setNameColorFor(viewer, NamedTextColor.YELLOW)
                            } else {
                                this.player.mannequinController.setNameColorFor(player.uuid, NamedTextColor.YELLOW)
                            }
                        }
                    }
                }
            }
            val canSeeImposters = canSeeImposters()
            if (lastCanSeeImposters != canSeeImposters) {
                lastCanSeeImposters = canSeeImposters
                player.game.actionLog.add(CustomRoleActionElements.SnitchFinishedTasks(player.uuid))
                val thisPlayer = this.player.humanOrNull?.player
                for (player in player.game.players) {
                    if (player.role.definition.team == Team.IMPOSTERS) {
                        if (thisPlayer != null) {
                            player.mannequinController.setNameColorFor(thisPlayer, NamedTextColor.RED)
                        } else if (this.player.isHuman) {
                            player.mannequinController.setNameColorFor(this.player.uuid, NamedTextColor.RED)
                        }
                    }
                }
            }
        }

        override fun createBotVoteTargetController() = SnitchBotVoteTargetController(this)

        companion object {
            private val SCOREBOARD_LINE_FINISHED = Component.translatable("role.snitch.scoreboard.finished")
            private val SCOREBOARD_LINE_LEFT = Component.translatable("role.snitch.scoreboard.one_task_left")
            private val SCOREBOARD_LINE_WAITING = Component.translatable("role.snitch.scoreboard.wait_for_start")
            private val WARNING = Component.translatable("role.snitch.warning")
        }
    }

    /**
     * The controller will prioritize voting for imposters that if he can see imposters.
     */
    class SnitchBotVoteTargetController(val role: AssignedSnitchRole) : BotVoteTargetController(
        role.player.botOrNull
            ?: throw IllegalArgumentException("Player must be a bot to create SnitchBotVoteTargetController")
    ) {
        private val random = default(role.player)

        override fun getTarget(availableTargets: List<AmongUsPlayer>): Target? = getTarget(availableTargets, true)

        override fun getTarget(availableTargets: List<AmongUsPlayer>, includeSkip: Boolean): Target? {
            val filteredTargets =
                if (role.canSeeImposters()) availableTargets.filter { it.role.definition.team == Team.IMPOSTERS }
                else availableTargets
            return random.getTarget(filteredTargets, includeSkip)
        }
    }
}