package com.fantamomo.mc.amongus.ability

import com.fantamomo.mc.amongus.player.AmongUsPlayer

interface Ability<A : Ability<A, S>, S : AssignedAbility<A, S>> {
    val id: String

    fun assignTo(player: AmongUsPlayer): S
}