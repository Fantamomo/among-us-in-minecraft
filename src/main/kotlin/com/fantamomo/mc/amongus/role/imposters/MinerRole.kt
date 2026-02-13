package com.fantamomo.mc.amongus.role.imposters

import com.fantamomo.mc.amongus.ability.abilities.CreateVentAbility
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team

object MinerRole : Role<MinerRole, MinerRole.AssignedMinerRole> {
    override val id: String = "miner"
    override val team: Team = Team.IMPOSTERS
    override val defaultAbilities = ImposterRole.defaultAbilities + CreateVentAbility

    override fun assignTo(player: AmongUsPlayer) = AssignedMinerRole(player)

    class AssignedMinerRole(override val player: AmongUsPlayer) : AssignedRole<MinerRole, AssignedMinerRole> {
        override val definition: MinerRole = MinerRole
    }
}