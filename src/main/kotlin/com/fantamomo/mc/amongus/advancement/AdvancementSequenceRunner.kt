package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.advancement.utils.step.StepInfo

@ExperimentalAdvancementApi
class AdvancementSequenceRunner(
    val runner: suspend AdvancementSequence.() -> Unit,
    /**
     * The top-level `step("...")` calls inside [runner] in source order, nested steps are in [StepInfo.children].
     *
     * Extracted at compile time by the `advancement-compiler-plugin`, the runner is never executed for this.
     */
    val steps: List<StepInfo>,
    /**
     * Lazy, because [createRunner] is called while the advancement itself is still being initialized.
     */
    advancement: () -> SequencedAdvancement,
) {
    val advancement: SequencedAdvancement by lazy(advancement)

    /** All steps (nested ones included) in pre-order, i.e. parents before their children. */
    val allSteps: List<StepInfo> = steps.flatMap { it.flatten() }

    /** All steps that are shown in the GUI, in pre-order. */
    val visibleSteps: List<StepInfo>

    private val stepsByKey: Map<String, StepInfo> = allSteps.associateBy { it.key }

    init {
        require(stepsByKey.size == allSteps.size) { "Step keys must be unique: ${allSteps.map { it.key }}" }
        for ([index, step] in steps.withIndex()) step.bind(this, null, index)
        StepInfo.assignDisplayIndices(steps)
        visibleSteps = allSteps.filter { it.isVisible }
    }

    fun stepOrNull(key: String): StepInfo? = stepsByKey[key]

    fun step(key: String): StepInfo = stepsByKey[key]
        ?: error("Unknown step '$key' in ${advancement.id}, is the advancement-compiler-plugin applied?")
}

/**
 * This is used to define the sequence of actions that need to be completed to complete an advancement.
 * See [AdvancementSequence] for everything that can be used inside the [runner].
 *
 * Because of how the system works, there are some rules:
 * 1. The runner must not keep state outside itself, it can be canceled at any time and several attempts run in parallel.
 * 2. Returning normally means "all requirements are met", the advancement is awarded.
 *    To fail an attempt, throw (`stepFailed()`, [AdvancementSequence.fail], timeouts, guards), **never** just `return`.
 * 3. The runner must wait for something (an event, a timer, ...) before it can fail or finish,
 *    otherwise the advancement is disabled for the rest of the game.
 *
 * # Why are several attempts running in parallel?
 *
 * Think of a sequence that requires the player to kill 5 players within 5 minutes:
 *
 * ```
 * step("first_kill") { awaitOwnKill() }
 * withTimeout(5.minutes) {
 *     repeat(4) { awaitOwnKill() }
 * }
 * ```
 *
 * Now take this run:
 *
 * **00:00** kill, **01:20** kill, **02:32** kill, **03:40** kill, **05:04** kill, **06:00** kill
 *
 * The attempt that started at 00:00 fails, the fifth kill was 4 seconds too late.
 * But starting at 01:20, the player killed 5 players within 4 minutes and 40 seconds.
 *
 * ## The fix
 *
 * As soon as the first action of an attempt is done (see [AdvancementSequence]), the system starts the next attempt,
 * which waits for the first action again. So every possible starting point gets its own attempt,
 * without the runner having to handle overlapping time windows itself.
 *
 * ### Parts
 *
 * [steps] is filled in automatically by the `advancement-compiler-plugin`, do not pass it manually.
 * See [AdvancementSequenceRunner.steps] and [StepInfo].
 *
 * @author Fantamomo
 * @since 2.4-SNAPSHOT
 */
@Suppress("NOTHING_TO_INLINE") // inline for the `advancement-compiler-plugin`
@ExperimentalAdvancementApi
inline fun SequencedAdvancement.createRunner(
    steps: Array<StepInfo> = emptyArray(),
    noinline runner: suspend AdvancementSequence.() -> Unit
) = AdvancementSequenceRunner(runner, steps.asList()) { this }
