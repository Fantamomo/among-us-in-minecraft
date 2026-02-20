package com.fantamomo.mc.amongus.role.crewmates

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.abilities.RevealTeamAbility
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team

object SeerRole : Role<SeerRole, SeerRole.AssignedSeerRole> {
    override val id: String = "seer"
    override val team: Team = Team.CREWMATES
    override val defaultAbilities: Set<Ability<*, *>> = setOf(RevealTeamAbility)

    override fun assignTo(player: AmongUsPlayer) = AssignedSeerRole(player)

    class AssignedSeerRole(override val player: AmongUsPlayer) : AssignedRole<SeerRole, AssignedSeerRole> {
        override val definition = SeerRole
    }
}