package com.fantamomo.mc.amongus.advancement.utils.step

import com.fantamomo.mc.amongus.advancement.AdvancementSequence
import com.fantamomo.mc.amongus.advancement.AdvancementSequencePart
import com.fantamomo.mc.amongus.advancement.ExperimentalAdvancementApi
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@ExperimentalAdvancementApi
context(stepContext: StepContext)
fun stepFailed(): Nothing = throw StepFailedException(stepId = stepContext.id)


/**
 * Marks [block] as a named step of the sequence.
 *
 * All parameters except [block] are only read at compile time by the `advancement-compiler-plugin`, which stores them
 * in the [StepInfo] of this step (see [StepInfo] on how to add a new parameter). At runtime the step is looked up by [key].
 *
 * @param key the unique (per advancement) key of this step, must be a constant
 * @param languageKey the language key describing this step, defaults to [StepInfo.defaultLanguageKey]
 */
@OptIn(ExperimentalContracts::class)
@ExperimentalAdvancementApi
@Suppress("unused") // parameters are read by the compiler plugin
internal suspend inline fun <R> AdvancementSequence.step(
    key: String,
    languageKey: String? = null,
    /**
     * `crossinline` because the compiler **is not able to** inline this suspends lambda if you use a non-local `return` statement.
     */
    crossinline block: suspend context(StepContext) AdvancementSequence.() -> R
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        returnsResultOf(block)
    }
    val info = advancement.runner.step(key)
    return withPart(AdvancementSequencePart.NamedPart(info)) {
        with(StepContext(info)) {
            block()
        }
    }
}
