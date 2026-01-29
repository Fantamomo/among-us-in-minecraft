package com.fantamomo.mc.amongus.ability.builder

import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import org.bukkit.inventory.ItemStack

class DSLAbilityItem(
    ability: AssignedAbility<*, *>,
    id: String,

    private val ctx: AbilityContext,

    private val states: Map<AbilityItemState, AbilityItemStateDefinition>,

    private val conditions: List<AbilityCondition>

) : AbilityItem(ability, id) {

    private var lastState: AbilityItemState? = null

    init {
        ctx.abilityItem = this
    }

    private fun computeState(): Pair<AbilityItemState, BlockReason?> {

        val reason = conditions.firstNotNullOfOrNull { ctx.it() }

        if (reason != null)
            return AbilityItemState.BLOCKED to reason

        val cooldownTimer = ctx.getTimer("cooldown")

        if (cooldownTimer != null && !cooldownTimer.isFinished())
            return AbilityItemState.COOLDOWN to null

        return AbilityItemState.ACTIVE to null
    }

    override fun getItemStack(): ItemStack {
        val (state, _) = computeState()

        if (state != lastState) {
            lastState?.let { states[it]?.onExit?.invoke(ctx) }
            states[state]?.onEnter?.invoke(ctx)
            lastState = state
        }

        return states[state]!!.render(ctx)
    }

    override fun onRightClick() {
        val (state, _) = computeState()

        states[state]!!.onRightClick(ctx)

        if (state == AbilityItemState.ACTIVE) {
            ctx.getTimer("cooldown")?.start()
        }

        notifyItemChange()
    }

    override fun onLeftClick() {

        val (state, _) = computeState()

        states[state]!!.onLeftClick(ctx)

        notifyItemChange()
    }
}