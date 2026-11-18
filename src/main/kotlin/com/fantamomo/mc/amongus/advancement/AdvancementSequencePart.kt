package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.advancement.utils.step.StepInfo
import com.fantamomo.mc.amongus.events.AmongUsEvent

sealed interface AdvancementSequencePart {

    @ExperimentalAdvancementApi
    data class NamedPart(val step: StepInfo) : AdvancementSequencePart {
        val key: String get() = step.key
        val languageKey: String get() = step.languageKey
    }

    sealed interface Blocking : AdvancementSequencePart {
        data class AwaitingEvent(val event: Class<AmongUsEvent>) : Blocking

        data object AwaitingOwnPlayer : Blocking

        data object AwaitingCustomLogic : Blocking
    }
}
