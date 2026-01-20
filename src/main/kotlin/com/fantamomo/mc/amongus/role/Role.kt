package com.fantamomo.mc.amongus.role

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.player.AmongUsPlayer

interface Role<R : Role<R, A>, A : AssignedRole<R, A>> {
    val id: String
    val team: Team
    val canDoTask: Boolean
        get() = team.canDoTask

    /** A translation Key */
    val name: String

    val defaultAbilities: Set<Ability<*, *>>

    fun assignTo(player: AmongUsPlayer): A
}