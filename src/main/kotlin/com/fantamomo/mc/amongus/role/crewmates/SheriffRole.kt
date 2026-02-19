package com.fantamomo.mc.amongus.role.crewmates

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.abilities.SheriffKillAbility
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team
import com.fantamomo.mc.amongus.role.marker.KillerRole

object SheriffRole : Role<SheriffRole, SheriffRole.AssignedSheriffRole>, KillerRole {
    override val id: String = "sheriff"
    override val team: Team = Team.CREWMATES
    override val defaultAbilities: Set<Ability<*, *>> = setOf(SheriffKillAbility)

    override fun assignTo(player: AmongUsPlayer) = AssignedSheriffRole(player)

    class AssignedSheriffRole(override val player: AmongUsPlayer) : AssignedRole<SheriffRole, AssignedSheriffRole> {
        override val definition = SheriffRole
    }
}