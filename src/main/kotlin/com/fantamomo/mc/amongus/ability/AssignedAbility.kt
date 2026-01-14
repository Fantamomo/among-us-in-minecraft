package com.fantamomo.mc.amongus.ability

import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.player.AmongUsPlayer

interface AssignedAbility<A : Ability<A, S>, S : AssignedAbility<A, S>> {
    val definition: A
    val player: AmongUsPlayer
    val items: List<AbilityItem>
}