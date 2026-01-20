package com.fantamomo.mc.amongus.role.imposters

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.abilities.KillAbility
import com.fantamomo.mc.amongus.ability.abilities.SabotageAbility
import com.fantamomo.mc.amongus.ability.abilities.VentAbility
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.role.AssignedRole
import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team

object ImposterRole : Role<ImposterRole, ImposterRole.AssignedImposterRole> {
    override val id: String = "imposter"
    override val team: Team = Team.IMPOSTERS
    override val name: String = "role.imposter.name"
    override val defaultAbilities: Set<Ability<*, *>> = setOf(
        KillAbility,
        VentAbility,
        SabotageAbility
    )

    override fun assignTo(player: AmongUsPlayer) = AssignedImposterRole(player)

    class AssignedImposterRole(override val player: AmongUsPlayer) : AssignedRole<ImposterRole, AssignedImposterRole> {
        override val definition = ImposterRole
    }
}