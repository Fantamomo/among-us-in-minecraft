package com.fantamomo.mc.amongus.role.crewmates

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.abilities.CallMeetingAbility
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team

object CallerRole : Role<CallerRole, CallerRole.AssignedCallerRole> {
    override val id: String = "caller"
    override val team: Team = Team.CREWMATES
    override val defaultAbilities: Set<Ability<*, *>> = setOf(
        CallMeetingAbility
    )

    override fun assignTo(player: AmongUsPlayer) = AssignedCallerRole(player)

    class AssignedCallerRole(override val player: AmongUsPlayer) : AssignedRole<CallerRole, AssignedCallerRole> {
        override val definition = CallerRole
    }
}