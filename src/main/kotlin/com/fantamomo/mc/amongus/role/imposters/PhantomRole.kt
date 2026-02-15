package com.fantamomo.mc.amongus.role.imposters

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.abilities.GhostFormAbility
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team

object PhantomRole : Role<PhantomRole, PhantomRole.AssignedPhantomRole> {
    override val id: String = "phantom"
    override val team: Team = Team.IMPOSTERS
    override val defaultAbilities: Set<Ability<*, *>> = ImposterRole.defaultAbilities + GhostFormAbility

    override fun assignTo(player: AmongUsPlayer) = AssignedPhantomRole(player)

    class AssignedPhantomRole(override val player: AmongUsPlayer) : AssignedRole<PhantomRole, AssignedPhantomRole> {
        override val definition = PhantomRole
    }
}