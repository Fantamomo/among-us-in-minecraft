package com.fantamomo.mc.amongus.ability.builder

import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.util.Cooldown
import java.util.*
import kotlin.time.Duration

class AbilityItemBuilder(
    val ability: AssignedAbility<*, *>,
    val id: String
) {

    val ctx = AbilityContext(this, ability)

    private val states = EnumMap<AbilityItemState, AbilityItemStateDefinition>(
        AbilityItemState::class.java
    )

    internal val conditions = mutableListOf<AbilityCondition>()

    init {
        AbilityItemState.entries.forEach {
            states[it] = AbilityItemStateDefinition()
        }
    }

    fun setTimer(id: String, cooldown: Cooldown) = ctx.setTimer(id, cooldown)

    fun timer(id: String, duration: Duration) = ctx.timer(id, duration)

    fun state(
        state: AbilityItemState,
        block: AbilityItemStateDefinition.() -> Unit
    ) {
        states[state]!!.apply(block)
    }

    fun condition(block: AbilityCondition) {
        conditions += block
    }

    fun build(): AbilityItem =
        DSLAbilityItem(
            ability,
            id,
            ctx,
            states,
            conditions
        )
}