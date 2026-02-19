package com.fantamomo.mc.amongus.role.neutral

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.abilities.EatBodyAbility
import com.fantamomo.mc.amongus.languages.numeric
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.settings.SettingsKey
import net.kyori.adventure.text.Component

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
            get() = player.game.settings[SettingsKey.ROLES.CANNIBAL_BODIES_TO_EAT]

        var eatenBodies: Int = 0
            private set

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