package com.fantamomo.mc.amongus.role.crewmates

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team

object MayorRole : Role<MayorRole, MayorRole.AssignedMayorRole> {
    override val id: String = "mayor"
    override val team: Team = Team.CREWMATES
    override val defaultAbilities: Set<Ability<*, *>> = setOf()

    override fun assignTo(player: AmongUsPlayer) = AssignedMayorRole(player)

    class AssignedMayorRole(override val player: AmongUsPlayer) : AssignedRole<MayorRole, AssignedMayorRole> {
        override val definition = MayorRole
    }
}