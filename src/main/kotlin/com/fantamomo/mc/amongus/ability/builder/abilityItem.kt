package com.fantamomo.mc.amongus.ability.builder

import com.fantamomo.mc.amongus.ability.Ability
import com.fantamomo.mc.amongus.ability.AssignedAbility
import com.fantamomo.mc.amongus.ability.item.AbilityItem
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
fun <A : Ability<A, S>, S : AssignedAbility<A, S>> S.abilityItem(
    id: String,
    block: AbilityItemBuilder<A, S>.() -> Unit
): AbilityItem {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    val builder = AbilityItemBuilder<A, S>(this, id)
    builder.block()
    return builder.build()
}