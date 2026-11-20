package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.advancement.utils.step.StepDeclaration
import com.fantamomo.mc.amongus.advancement.utils.step.StepInfo

/**
 * Handle of a branch of [AdvancementSequence.allOf] or [AdvancementSequence.race].
 *
 * @author Fantamomo
 * @since 2.5-SNAPSHOT
 */
@ExperimentalAdvancementApi
class Branch<T> internal constructor(
    internal val step: StepInfo?,
    internal val block: suspend AdvancementSequence.() -> T,
) {
    private var value: Any? = NOT_COMPLETED

    /** True if this branch completed successfully (in a race: if it won). */
    val isCompleted: Boolean get() = value !== NOT_COMPLETED

    /** The result of this branch, only available after it completed. */
    val result: T
        get() {
            check(isCompleted) { "Branch ${step?.key ?: ""} has not completed" }
            @Suppress("UNCHECKED_CAST")
            return value as T
        }

    internal fun complete(value: Any?) {
        this.value = value
    }

    private object NOT_COMPLETED
}

/**
 * Builder of [AdvancementSequence.allOf], every branch must complete.
 *
 * The parameters of the named [branch] are the same as the ones of `step(...)` and are read by the
 * `advancement-compiler-plugin` in the same way.
 */
@ExperimentalAdvancementApi
class AllOfScope internal constructor(private val sequence: AdvancementSequence) {
    internal val branches = mutableListOf<Branch<*>>()

    /** A named branch, shown as a step in the GUI. */
    @StepDeclaration
    @Suppress("unused") // parameters are read by the compiler plugin
    fun <T> branch(
        key: String,
        languageKey: String? = null,
        hidden: Boolean = false,
        timeoutSeconds: Int = 0,
        block: suspend AdvancementSequence.() -> T,
    ): Branch<T> = Branch(sequence.runner.step(key), block).also { branches += it }

    /** An unnamed branch, not shown in the GUI. */
    fun <T> branch(block: suspend AdvancementSequence.() -> T): Branch<T> =
        Branch(null, block).also { branches += it }
}

/**
 * Builder of [AdvancementSequence.race], the first branch that completes wins.
 *
 * @see AllOfScope
 */
@ExperimentalAdvancementApi
class RaceScope<T> internal constructor(private val sequence: AdvancementSequence) {
    internal val branches = mutableListOf<Branch<T>>()

    /** A named branch, shown as a step in the GUI. */
    @StepDeclaration
    @Suppress("unused") // parameters are read by the compiler plugin
    fun branch(
        key: String,
        languageKey: String? = null,
        hidden: Boolean = false,
        timeoutSeconds: Int = 0,
        block: suspend AdvancementSequence.() -> T,
    ): Branch<T> = Branch(sequence.runner.step(key), block).also { branches += it }

    /** An unnamed branch, not shown in the GUI. */
    fun branch(block: suspend AdvancementSequence.() -> T): Branch<T> =
        Branch(null, block).also { branches += it }
}
