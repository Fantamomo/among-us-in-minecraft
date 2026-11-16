package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.events.PlayerAmongUsEvent
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
@ExperimentalAdvancementApi
suspend fun <E : PlayerAmongUsEvent> AdvancementSequence.awaitOwnPlayer(block: suspend AdvancementSequence.() -> E): E {
    contract {
        callsInPlace(block, InvocationKind.AT_LEAST_ONCE)
        returnsResultOf(block)
    }
    ensureActive()
    withBlocker(AdvancementSequenceBlocker.AwaitingOwnPlayer()) {
        while (true) {
            val event = block()
            ensureActive()
            if (event.player == player) return event
        }
    }
}

@ExperimentalAdvancementApi
suspend fun <R> AdvancementSequence.awaitCustomLogic(block: suspend AdvancementSequence.() -> R): R {
    ensureActive()
    withBlocker(AdvancementSequenceBlocker.AwaitingCustomLogic()) {
        return block()
    }
}