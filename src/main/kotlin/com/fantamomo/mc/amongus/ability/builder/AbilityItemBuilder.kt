package com.fantamomo.mc.amongus.ability.builder

import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.player.bot.AmongUsZombie
import com.fantamomo.mc.amongus.util.Cooldown
import net.kyori.adventure.text.Component
import net.minecraft.world.entity.ai.goal.GoalSelector
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

    private var registerGoals: (GoalSelector, AmongUsZombie, DSLAbilityItem) -> Unit = { _, _, _ -> }

    internal val conditions = mutableListOf<AbilityCondition>()

    var clickDelay: Boolean = false

    init {
        AbilityItemState.entries.forEach {
            states[it] = it.createDefault()
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

    fun registerGoals(block: (GoalSelector, AmongUsZombie, DSLAbilityItem) -> Unit) {
        registerGoals = block
    }

    /**
     * Adds a condition that determines whether the assigned ability should be blocked based on custom logic.
     *
     * @param reason The reason for blocking the ability. This can be one of the predefined block reasons
     * (e.g., `InMeeting`, `Sabotage`, `Dead`) or a custom-defined reason.
     * @param tooltip An optional tooltip message displayed to indicate the reason for blocking.
     * @param block The lambda function containing the logic to determine whether the ability should be blocked.
     * The function should return `true` if the ability should be blocked given the current `AbilityContext`, or
     * `false` otherwise.
     */
    fun condition(reason: BlockReason, tooltip: Component? = null, block: AbilityContext.() -> Boolean) {
        val conditionImpl = if (tooltip != null)
            TooltipAbilityCondition.Impl(reason, tooltip, block)
        else BooleanAbilityCondition.Impl(reason, block)
        condition(conditionImpl)
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
            conditions as List<AbilityCondition>,
            clickDelay,
            registerGoals
        )
}