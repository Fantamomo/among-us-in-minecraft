package com.fantamomo.mc.amongus.ability.builder

import com.fantamomo.mc.amongus.ability.AssignedAbility

class AbilityContext(
    val ability: AssignedAbility<*, *>
) {
    val player = ability.player
    val game = player.game
}