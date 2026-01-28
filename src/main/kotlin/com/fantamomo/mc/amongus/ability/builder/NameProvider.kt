package com.fantamomo.mc.amongus.ability.builder

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility

class NameProvider<A : Ability<A, S>, S : AssignedAbility<A, S>> {
    private var active: (AbilityContext<A, S>) -> String = { "" }
    private var inactive: (AbilityContext<A, S>, BlockReason?) -> String = { _, _ -> "" }
    var cooldown: String = "ability.general.disabled.cooldown"

    fun active(key: String) {
        active = { key }
    }

    fun active(block: AbilityContext<A, S>.() -> String) {
        active = block
    }

    fun inactive(key: String) {
        inactive = { _, _ -> key }
    }

    fun inactive(block: InactiveNameScope<A, S>.() -> Unit) {
        val scope = InactiveNameScope<A, S>()
        scope.block()
        inactive = scope::resolve
    }

    internal fun active(ctx: AbilityContext<A, S>) = active.invoke(ctx)
    internal fun inactive(ctx: AbilityContext<A, S>, reason: BlockReason?) = inactive.invoke(ctx, reason)
}

class InactiveNameScope<A : Ability<A, S>, S : AssignedAbility<A, S>> {
    private val map = mutableMapOf<BlockReason, String>()
    private var fallback: String = ""

    fun whenBlocked(reason: BlockReason, key: String) {
        map[reason] = key
    }

    fun whenBlocked(id: String, key: String) {
        map[BlockReason.Custom(id)] = key
    }

    fun otherwise(key: String) {
        fallback = key
    }

    fun resolve(ctx: AbilityContext<A, S>, reason: BlockReason?) =
        map[reason] ?: fallback
}
