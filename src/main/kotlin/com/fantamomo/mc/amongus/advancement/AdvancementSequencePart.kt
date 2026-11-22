package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.advancement.utils.step.StepInfo
import com.fantamomo.mc.amongus.events.AmongUsEvent
import kotlin.time.Duration

/**
 * What a fiber of a sequence is currently doing, used for debugging (see [AdvancementSequence.describe]).
 *
 * [Blocking] parts are the "await regions" used to detect the first action of a sequence (see [AdvancementSequence]).
 */
sealed interface AdvancementSequencePart {

    @ExperimentalAdvancementApi
    data class NamedPart(val step: StepInfo) : AdvancementSequencePart {
        val key: String get() = step.key
        val languageKey: String get() = step.languageKey

        override fun toString() = "step(${step.key})"
    }

    data class Timeout(val duration: Duration) : AdvancementSequencePart

    data class Guard(val event: Class<out AmongUsEvent>) : AdvancementSequencePart

    sealed interface Blocking : AdvancementSequencePart {
        data class AwaitingEvents(val events: List<Class<out AmongUsEvent>>) : Blocking {
            override fun toString() = "await(${events.joinToString { it.simpleName }})"
        }

        data object AwaitingOwnPlayer : Blocking

        data object AwaitingCustomLogic : Blocking

        data object AwaitingCondition : Blocking

        data object AwaitingInfinity : Blocking

        data class Delay(val duration: Duration) : Blocking

        data class Parallel(val requireAll: Boolean, val branches: Int) : Blocking
    }
}
