package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.events.AmongUsEvent
import kotlin.coroutines.Continuation

sealed interface AdvancementSequenceBlocker {
    val continuation: Continuation<*>?

    data class AwaitingEvent(override var continuation: Continuation<*>?, val event: Class<AmongUsEvent>) : AdvancementSequenceBlocker

    data class AwaitingOwnPlayer(override val continuation: Continuation<*>? = null) : AdvancementSequenceBlocker

    data class AwaitingCustomLogic(override val continuation: Continuation<*>? = null) : AdvancementSequenceBlocker
}