package com.fantamomo.mc.amongus.role.crewmates

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.abilities.RevealTeamAbility
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.bot.mangement.BotVoteTargetController
import com.fantamomo.mc.amongus.player.botOrNull
import com.fantamomo.mc.amongus.player.isAlive
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.SupportBotsRole
import com.fantamomo.mc.amongus.role.Team
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration

object SeerRole : Role<SeerRole, SeerRole.AssignedSeerRole> {
    override val id: String = "seer"
    override val team: Team = Team.CREWMATES
    override val defaultAbilities: Set<Ability<*, *>> = setOf(RevealTeamAbility)

    override fun assignTo(player: AmongUsPlayer) = AssignedSeerRole(player)

    class AssignedSeerRole(override val player: AmongUsPlayer) : AssignedRole<SeerRole, AssignedSeerRole>, SupportBotsRole {
        override val definition = SeerRole
        internal val revealedPlayers = mutableListOf<AmongUsPlayer>()

        fun addRevealedPlayer(player: AmongUsPlayer) {
            revealedPlayers.add(player)
        }

        override fun gameEndInfo(): Component {
            val revealedPlayersInfo = revealedPlayers.map {
                Component.text(it.name, it.role.definition.team.textColor)
            }
            return Component.join(joinConfig, revealedPlayersInfo)
        }

        override fun createBotVoteTargetController() = SeerBotVoteTargetController(this)

        companion object {
            private val joinConfig = JoinConfiguration.separator(Component.translatable("role.seer.end.separator"))
        }
    }

    /**
     * The bot will prioritize voting for revealed imposters, then neutrals.
     *
     * It will never vote on crewmates that have been revealed.
     *
     * It falls back to a random vote target if no valid targets are found.
     */
    class SeerBotVoteTargetController(val role: AssignedSeerRole) : BotVoteTargetController(
        role.player.botOrNull
            ?: throw IllegalArgumentException("Player must be a bot to create SeerBotVoteTargetController")
    ) {
        private val random = default(role.player)

        override fun getTarget(availableTargets: List<AmongUsPlayer>): Target? {
            val revealedPlayers = role.revealedPlayers.filter { it.isAlive() }
            if (revealedPlayers.isEmpty()) return random.getTarget(availableTargets)

            val imposters = revealedPlayers.filter { it.role.definition.team == Team.IMPOSTERS }
            if (imposters.isNotEmpty()) return random.getTarget(imposters)

            val neutral =
                revealedPlayers.filter { it.role.definition.team.let { team -> team is Team.NEUTRAL && team != Team.NEUTRAL.JESTER } }
            if (neutral.isNotEmpty()) return random.getTarget(neutral)

            val targets = availableTargets.filter { revealedPlayers.contains(it) }
            return random.getTarget(targets)
        }
    }
}