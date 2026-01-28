package com.fantamomo.mc.amongus.ability.builder

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.util.Cooldown
import org.bukkit.Material

class AbilityItemBuilder<A : Ability<A, S>, S : AssignedAbility<A, S>>(
    val ability: S,
    val id: String
) {
    val ctx = AbilityContext(ability)

    var activeMaterial: Material = Material.BARRIER
    var inactiveMaterial: Material = Material.BARRIER

    var cooldown: Cooldown? = null

    internal val blockers = mutableListOf<(AbilityContext<A, S>) -> BlockReason?>()

    internal var rightClick: (AbilityContext<A, S>) -> Unit = {}
    internal var leftClick: (AbilityContext<A, S>) -> Unit = {}

    internal val nameProvider = NameProvider<A, S>()

    fun material(block: MaterialScope<A, S>.() -> Unit) {
        MaterialScope(this).apply(block)
    }

    fun cooldown(duration: Cooldown) {
        this.cooldown = duration
    }

    fun cooldown(block: AbilityContext<A, S>.() -> Cooldown) {
        cooldown = ctx.block()
    }

    fun name(block: NameProvider<A, S>.() -> Unit) {
        nameProvider.apply(block)
    }

    fun blockWhen(block: BlockScope<A, S>.() -> Unit) {
        val scope = BlockScope<A, S>()
        scope.block()
        blockers += scope.conditions
    }

    fun require(block: RequireScope<A, S>.() -> Unit) {
        val scope = RequireScope<A, S>()
        scope.block()
        blockers += scope.conditions
    }

    fun onRightClick(block: AbilityContext<A, S>.() -> Unit) {
        rightClick = block
    }

    fun onLeftClick(block: AbilityContext<A, S>.() -> Unit) {
        leftClick = block
    }

    fun build(): AbilityItem = DSLAbilityItem(this)
}