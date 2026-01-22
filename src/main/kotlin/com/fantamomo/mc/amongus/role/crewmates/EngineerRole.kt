package com.fantamomo.mc.amongus.role.crewmates

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.abilities.VentAbility
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team

object EngineerRole : Role<EngineerRole, EngineerRole.AssignedEngineerRole> {
    override val id: String = "engineer"
    override val team: Team = Team.CREWMATES
    override val defaultAbilities: Set<Ability<*, *>> = setOf(
        VentAbility
    )

    override fun assignTo(player: AmongUsPlayer) = AssignedEngineerRole(player)

    class AssignedEngineerRole(override val player: AmongUsPlayer) : AssignedRole<EngineerRole, AssignedEngineerRole> {
        override val definition = EngineerRole
    }
}