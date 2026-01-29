package com.fantamomo.mc.amongus.ability.builder

import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.util.Cooldown
import kotlin.time.Duration

class AbilityContext(
    private val builder: AbilityItemBuilder,
    val ability: AssignedAbility<*, *>
) {

    val player = ability.player
    val game = player.game

    internal lateinit var abilityItem: AbilityItem

    private val timers = mutableMapOf<String, AbilityTimer>()

    fun timer(id: String, duration: Duration): AbilityTimer =
        timers.getOrPut(id) { AbilityTimer(id, duration) }

    fun getTimer(id: String): AbilityTimer? =
        timers[id]

    fun stopTimer(id: String) {
        timers[id]?.stop()
    }

    fun refresh() {
        player.notifyAbilityItemChange(abilityItem)
    }

    fun getBlockReason(): BlockReason? = builder.conditions.firstNotNullOfOrNull { it.invoke(this) }

    fun setTimer(id: String, cooldown: Cooldown): AbilityTimer =
        timers.getOrPut(id) { AbilityTimer(id, cooldown) }
}