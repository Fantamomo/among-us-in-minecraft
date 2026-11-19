@file:OptIn(ExperimentalContracts::class)

package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.events.PlayerAmongUsEvent
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@ExperimentalAdvancementApi
suspend fun <E : PlayerAmongUsEvent> AdvancementSequence.awaitOwnPlayer(block: suspend AdvancementSequence.() -> E): E {
    contract {
        callsInPlace(block, InvocationKind.AT_LEAST_ONCE)
        returnsResultOf(block)
    }
    ensureActive()
    withPart(AdvancementSequencePart.Blocking.AwaitingOwnPlayer) {
        while (true) {
            val event = block()
            ensureActive()
            if (event.player == player) return event
        }
    }
}

@ExperimentalAdvancementApi
suspend fun <R> AdvancementSequence.awaitCustomLogic(block: suspend AdvancementSequence.() -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        returnsResultOf(block)
    }
    ensureActive()
    withPart(AdvancementSequencePart.Blocking.AwaitingCustomLogic) {
        return block()
    }
}

@ExperimentalAdvancementApi
suspend fun <T> AdvancementSequence.awaitCustomLogicFilter(
    provider: suspend AdvancementSequence.() -> T,
    filter: (T) -> Boolean
): T {
    contract {
        callsInPlace(provider, InvocationKind.AT_LEAST_ONCE)
        callsInPlace(filter, InvocationKind.AT_LEAST_ONCE)
        returnsResultOf(provider)
    }
    ensureActive()
    withPart(AdvancementSequencePart.Blocking.AwaitingCustomLogic) {
        while (true) {
            val result = provider()
            ensureActive()
            if (filter(result)) return result
        }
    }
}