package com.fantamomo.mc.amongus.role.crewmates

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.BotAmongUsPlayer
import com.fantamomo.mc.amongus.role.*

object MayorRole : Role<MayorRole, MayorRole.AssignedMayorRole> {
    override val id: String = "mayor"
    override val team: Team = Team.CREWMATES
    override val defaultAbilities: Set<Ability<*, *>> = setOf()

    override fun assignTo(player: AmongUsPlayer) = AssignedMayorRole(player)

    class AssignedMayorRole(override val player: AmongUsPlayer) : AssignedRole<MayorRole, AssignedMayorRole>,
        SupportBotsRole, RoleDescriptionPromptProvider {
        override val definition = MayorRole

        override fun getPromptPlaceholders(bot: BotAmongUsPlayer): Map<String, String> {
            val meeting = bot.game.meetingManager.meeting ?: return emptyMap()
            val hasVoted = meeting.hasVoted(bot, true)
            val voteTarget = meeting.getVoteTarget(bot, true)
            val target = when {
                !hasVoted -> "no one"
                voteTarget != null -> voteTarget.name
                else -> "skip"
            }
            return mapOf("mayor_vote" to target)
        }
    }
}