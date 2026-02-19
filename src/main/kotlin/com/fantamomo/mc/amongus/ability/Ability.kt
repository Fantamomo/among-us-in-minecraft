package com.fantamomo.mc.amongus.ability

import com.fantamomo.mc.amongus.player.AmongUsPlayer

interface Ability<A : Ability<A, S>, S : AssignedAbility<A, S>> {
    val id: String

    fun canAssignTo(player: AmongUsPlayer): Boolean = true

    fun assignTo(player: AmongUsPlayer): S
}