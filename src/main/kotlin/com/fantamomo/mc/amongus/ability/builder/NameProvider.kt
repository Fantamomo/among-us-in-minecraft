package com.fantamomo.mc.amongus.ability.builder

class NameProvider {
    private var active: (AbilityContext) -> String = { "" }
    private var inactive: (AbilityContext, BlockReason?) -> String = { _, _ -> "" }
    var cooldown: String = "ability.general.disabled.cooldown"

    fun active(key: String) {
        active = { key }
    }

    fun active(block: AbilityContext.() -> String) {
        active = block
    }

    fun inactive(key: String) {
        inactive = { _, _ -> key }
    }

    fun inactive(block: InactiveNameScope.() -> Unit) {
        val scope = InactiveNameScope()
        scope.block()
        inactive = scope::resolve
    }

    internal fun active(ctx: AbilityContext) = active.invoke(ctx)
    internal fun inactive(ctx: AbilityContext, reason: BlockReason?) = inactive.invoke(ctx, reason)
}

class InactiveNameScope {
    private val map = mutableMapOf<BlockReason, String>()
    private var fallback: String = ""

    fun whenBlocked(reason: BlockReason, key: String) {
        map[reason] = key
    }

    fun otherwise(key: String) {
        fallback = key
    }

    fun resolve(ctx: AbilityContext, reason: BlockReason?) =
        map[reason] ?: fallback
}
