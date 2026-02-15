package com.fantamomo.mc.amongus.role.crewmates

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team

object TheDamnedRole : Role<TheDamnedRole, TheDamnedRole.AssignedTheDamnedRole>{
    override val id: String = "the_damned"
    override val team: Team = Team.CREWMATES
    override val defaultAbilities: Set<Ability<*, *>> = setOf()

    override fun assignTo(player: AmongUsPlayer) = AssignedTheDamnedRole(player)

    class AssignedTheDamnedRole(override val player: AmongUsPlayer) : AssignedRole<TheDamnedRole, AssignedTheDamnedRole> {
        override val definition = TheDamnedRole
    }
}