package com.fantamomo.mc.amongus.role.crewmates

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team

object CrewmateRole : Role<CrewmateRole, CrewmateRole.AssignedCrewmateRole> {
    override val id: String = "crewmate"
    override val team: Team = Team.CREWMATES
    override val defaultAbilities: Set<Ability<*, *>> = setOf()

    override fun assignTo(player: AmongUsPlayer) = AssignedCrewmateRole(player)

    class AssignedCrewmateRole(override val player: AmongUsPlayer) : AssignedRole<CrewmateRole, AssignedCrewmateRole> {
        override val definition = CrewmateRole
    }
}