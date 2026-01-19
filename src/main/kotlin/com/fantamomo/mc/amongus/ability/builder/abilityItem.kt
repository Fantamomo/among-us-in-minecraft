package com.fantamomo.mc.amongus.ability.builder

import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
fun AssignedAbility<*, *>.abilityItem(
    id: String,
    block: AbilityItemBuilder.() -> Unit
): AbilityItem {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    val builder = AbilityItemBuilder(this, id)
    builder.block()
    return builder.build()
}