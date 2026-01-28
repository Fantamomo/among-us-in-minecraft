package com.fantamomo.mc.amongus.ability.builder

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import net.kyori.adventure.text.Component

class NameProvider<A : Ability<A, S>, S : AssignedAbility<A, S>> {
    private var active: (AbilityContext<A, S>) -> Component = { Component.empty() }
    private var inactive: (AbilityContext<A, S>, BlockReason?) -> Component = { _, _ -> Component.empty() }
    var cooldown: Component = DEFAULT_COOLDOWN_TRANSLATABLE

    fun active(key: String) {
        active(Component.translatable(key))
    }

    fun active(component: Component) {
        active = { component }
    }

    fun active(block: AbilityContext<A, S>.() -> Component) {
        active = block
    }

    fun inactive(key: String) {
        inactive(Component.translatable(key))
    }

    fun inactive(component: Component) {
        inactive = { _, _ -> component }
    }

    fun inactive(block: InactiveNameScope<A, S>.() -> Unit) {
        val scope = InactiveNameScope<A, S>()
        scope.block()
        inactive = scope::resolve
    }

    internal fun active(ctx: AbilityContext<A, S>) = active.invoke(ctx)
    internal fun inactive(ctx: AbilityContext<A, S>, reason: BlockReason?) = inactive.invoke(ctx, reason)

    companion object {
        val DEFAULT_COOLDOWN_TRANSLATABLE = Component.translatable("ability.general.disabled.cooldown")
    }

}

class InactiveNameScope<A : Ability<A, S>, S : AssignedAbility<A, S>> {
    private val map = mutableMapOf<BlockReason, Component>()
    private var fallback: Component = Component.empty()

    fun whenBlocked(reason: BlockReason, key: String) = whenBlocked(
        reason,
        Component.translatable(key)
    )

    fun whenBlocked(reason: BlockReason, component: Component) {
        map[reason] = component
    }

    fun whenBlocked(id: String, key: String) = whenBlocked(
        BlockReason.Custom(id),
        Component.translatable(key)
    )

    fun whenBlocked(id: String, component: Component) {
        map[BlockReason.Custom(id)] = component
    }

    fun otherwise(key: String) {
        fallback = Component.translatable(key)
    }

    fun otherwise(component: Component) {
        fallback = component
    }

    fun resolve(ctx: AbilityContext<A, S>, reason: BlockReason?) =
        map[reason] ?: fallback
}
