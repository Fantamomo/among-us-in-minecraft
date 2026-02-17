package com.fantamomo.mc.amongus.role.crewmates

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.languages.numeric
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.TitlePart

object SnitchRole : Role<SnitchRole, SnitchRole.AssignedSnitchRole> {
    override val id: String = "snitch"
    override val team: Team = Team.CREWMATES
    override val defaultAbilities: Set<Ability<*, *>> = setOf()

    override fun assignTo(player: AmongUsPlayer) = AssignedSnitchRole(player)

    class AssignedSnitchRole(override val player: AmongUsPlayer) : AssignedRole<SnitchRole, AssignedSnitchRole> {
        override val definition = SnitchRole

        private var lastCanSeeImposters = false
        private var sendWarning = false

        fun taskLeft(): Int = player.tasks.count { !it.completed }

        fun canSeeImposters(): Boolean = player.tasks.all { it.completed }

        override fun scoreboardLine(): Component? {
            if (!player.isAlive) return null
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

        override fun tick() {
            if (!player.isAlive) return
            if (taskLeft() <= 1) {
                if (!sendWarning) {
                    sendWarning = true
                    for (player in player.game.players) {
                        if (player.assignedRole?.definition?.team == Team.IMPOSTERS) {
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
                val thisPlayer = this.player.player
                for (player in player.game.players) {
                    if (player.assignedRole?.definition?.team == Team.IMPOSTERS) {
                        if (thisPlayer != null) {
                            player.mannequinController.setNameColorFor(thisPlayer, NamedTextColor.RED)
                        } else {
                            player.mannequinController.setNameColorFor(this.player.uuid, NamedTextColor.RED)
                        }
                    }
                }
            }
        }

        companion object {
            private val SCOREBOARD_LINE_FINISHED = Component.translatable("role.snitch.scoreboard.finished")
            private val SCOREBOARD_LINE_LEFT = Component.translatable("role.snitch.scoreboard.one_task_left")
            private val WARNING = Component.translatable("role.snitch.warning")
        }
    }
}