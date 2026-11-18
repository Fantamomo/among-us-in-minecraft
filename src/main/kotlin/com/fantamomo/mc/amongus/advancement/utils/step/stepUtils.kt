package com.fantamomo.mc.amongus.advancement.utils.step

import com.fantamomo.mc.amongus.advancement.AdvancementSequence
import com.fantamomo.mc.amongus.advancement.AdvancementSequencePart
import com.fantamomo.mc.amongus.advancement.ExperimentalAdvancementApi
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

context(stepContext: StepContext)
fun stepFailed(): Nothing = throw StepFailedException(stepId = stepContext.id)


@OptIn(ExperimentalContracts::class)
@ExperimentalAdvancementApi
internal suspend inline fun <R> AdvancementSequence.step(
    key: String,
    languageKey: String = "advancement.${advancement.id}.part.$key",
    /**
     * `crossinline` because the compiler **is not able to** inline this suspends lambda if you use a non-local `return` statement.
     */
    crossinline block: suspend context(StepContext) AdvancementSequence.() -> R
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        returnsResultOf(block)
    }
    return withPart(AdvancementSequencePart.NamedPart(key, languageKey)) {
        with(StepContext(key, languageKey)) {
            block()
        }
    }
}