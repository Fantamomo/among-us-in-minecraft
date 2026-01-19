package com.fantamomo.mc.amongus.ability.builder

import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import com.fantamomo.mc.amongus.util.Cooldown
import org.bukkit.Material

class AbilityItemBuilder(
    val ability: AssignedAbility<*, *>,
    val id: String
) {
    val ctx = AbilityContext(ability)

    var activeMaterial: Material = Material.BARRIER
    var inactiveMaterial: Material = Material.BARRIER

    var cooldown: Cooldown? = null

    internal val blockers = mutableListOf<(AbilityContext) -> BlockReason?>()

    internal var rightClick: (AbilityContext) -> Unit = {}
    internal var leftClick: (AbilityContext) -> Unit = {}

    internal val nameProvider = NameProvider()

    fun material(block: MaterialScope.() -> Unit) {
        MaterialScope(this).apply(block)
    }

    fun cooldown(duration: Cooldown) {
        this.cooldown = duration
    }

    fun cooldown(block: AbilityContext.() -> Cooldown) {
        cooldown = ctx.block()
    }

    fun name(block: NameProvider.() -> Unit) {
        nameProvider.apply(block)
    }

    fun blockWhen(block: BlockScope.() -> Unit) {
        val scope = BlockScope()
        scope.block()
        blockers += scope.conditions
    }

    fun require(block: RequireScope.() -> Unit) {
        val scope = RequireScope()
        scope.block()
        blockers += scope.conditions
    }

    fun onRightClick(block: AbilityContext.() -> Unit) {
        rightClick = block
    }

    fun onLeftClick(block: AbilityContext.() -> Unit) {
        leftClick = block
    }

    fun build(): AbilityItem = DSLAbilityItem(this)
}