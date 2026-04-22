package com.fantamomo.mc.amongus.role.imposters

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.ability.abilities.CreateVentAbility
import com.fantamomo.mc.amongus.languages.numeric
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.bot.mangement.BotVoteTargetController
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.SupportBotsRole
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.role.util.KillerRole

object MinerRole : Role<MinerRole, MinerRole.AssignedMinerRole>, KillerRole {
    override val id: String = "miner"
    override val team: Team = Team.IMPOSTERS
    override val defaultAbilities = ImposterRole.defaultAbilities + CreateVentAbility

    override fun assignTo(player: AmongUsPlayer) = AssignedMinerRole(player)

    class AssignedMinerRole(override val player: AmongUsPlayer) : AssignedRole<MinerRole, AssignedMinerRole>,
        SupportBotsRole {
        override val definition: MinerRole = MinerRole

        var createdVents = 0
            internal set

        override fun gameEndInfo() = textComponent {
            translatable("role.miner.end.created_vents") {
                args { numeric("count", createdVents) }
            }
        }

        override fun createBotVoteTargetController() = BotVoteTargetController.create { avoidOnesTeam(random(player)) }
    }
}