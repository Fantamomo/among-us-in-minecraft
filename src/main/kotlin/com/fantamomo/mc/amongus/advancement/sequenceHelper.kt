package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.events.PlayerAmongUsEvent

suspend inline fun <E : PlayerAmongUsEvent> AdvancementSequence.awaitOwnPlayer(block: suspend AdvancementSequence.() -> E): E {
    ensureActive()
    while (true) {
        val event = block()
        ensureActive()
        if (event.player == player) return event
    }
}