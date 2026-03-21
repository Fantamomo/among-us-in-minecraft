package com.fantamomo.mc.amongus.role.crewmates

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.abilities.RevealTeamAbility
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration

object SeerRole : Role<SeerRole, SeerRole.AssignedSeerRole> {
    override val id: String = "seer"
    override val team: Team = Team.CREWMATES
    override val defaultAbilities: Set<Ability<*, *>> = setOf(RevealTeamAbility)

    override fun assignTo(player: AmongUsPlayer) = AssignedSeerRole(player)

    class AssignedSeerRole(override val player: AmongUsPlayer) : AssignedRole<SeerRole, AssignedSeerRole> {
        override val definition = SeerRole
        private val revealedPlayers = mutableListOf<AmongUsPlayer>()

        fun addRevealedPlayer(player: AmongUsPlayer) {
            revealedPlayers.add(player)
        }

        override fun gameEndInfo(): Component {
            val revealedPlayersInfo = revealedPlayers.map {
                Component.text(it.name, it.role.definition.team.textColor)
            }
            return Component.join(joinConfig, revealedPlayersInfo)
        }

        companion object {
            private val joinConfig = JoinConfiguration.separator(Component.translatable("role.seer.end.separator"))
        }
    }
}