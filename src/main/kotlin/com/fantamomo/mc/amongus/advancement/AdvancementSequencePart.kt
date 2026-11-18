package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.events.AmongUsEvent

sealed interface AdvancementSequencePart {

    data class NamedPart(val key: String, val languageKey: String) : AdvancementSequencePart

    sealed interface Blocking : AdvancementSequencePart {
        data class AwaitingEvent(val event: Class<AmongUsEvent>) : Blocking

        data object AwaitingOwnPlayer : Blocking

        data object AwaitingCustomLogic : Blocking
    }
}