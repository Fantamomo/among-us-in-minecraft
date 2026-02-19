package com.fantamomo.mc.amongus.role.neutral

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team

object JesterRole : Role<JesterRole, JesterRole.AssignedJesterRole> {
    override val id: String = "jester"
    override val team: Team = Team.NEUTRAL.JESTER
    override val defaultAbilities: Set<Ability<*, *>> = setOf()

    override fun assignTo(player: AmongUsPlayer) = AssignedJesterRole(player)

    class AssignedJesterRole(override val player: AmongUsPlayer) : AssignedRole<JesterRole, AssignedJesterRole>{
        override val definition = JesterRole
    }
}