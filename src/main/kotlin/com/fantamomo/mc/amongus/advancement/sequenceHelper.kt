package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.events.PlayerAmongUsEvent
import kotlin.time.Duration

suspend inline fun <E : PlayerAmongUsEvent> AdvancementSequence.awaitOwnPlayer(block: suspend AdvancementSequence.() -> E): E {
    ensureActive()
    while (true) {
        val event = block()
        ensureActive()
        if (event.player == player) return event
    }
}

suspend fun <R> AdvancementSequence.withTimeout(duration: Duration, block: suspend AdvancementSequence.() -> R): R {
    addTimeoutStart(duration)
    val result = try {
        block()
    } finally {
        removeTimeout()
    }
    return result
}