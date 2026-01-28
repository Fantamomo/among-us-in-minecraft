package com.fantamomo.mc.amongus.ability.builder

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility

class AbilityContext<A : Ability<A, S>, S : AssignedAbility<A, S>>(
    val ability: S
) {
    val player = ability.player
    val game = player.game
}