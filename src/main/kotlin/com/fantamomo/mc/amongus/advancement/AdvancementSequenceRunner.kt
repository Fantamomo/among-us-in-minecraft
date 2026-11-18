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

    private val stepsByKey: Map<String, StepInfo> = allSteps.associateBy { it.key }

    init {
        require(stepsByKey.size == allSteps.size) { "Step keys must be unique: ${allSteps.map { it.key }}" }
        for ([index, step] in steps.withIndex()) step.bind(this, null, index)
    }

    fun stepOrNull(key: String): StepInfo? = stepsByKey[key]

    fun step(key: String): StepInfo = stepsByKey[key]
        ?: error("Unknown step '$key' in ${advancement.id}, is the advancement-compiler-plugin applied?")
}

/**
 * This is used to define the sequence of actions that need to be completed for to complete an advancement.
 *
 * Because of how the system works, there are some limitations and rules:
 * 1. Everything in this [runner], must be stateless, because it can be canceled at any time and can also be run in parallelly.
 *    (there is **no** guarantee on how it will be run)
 * 2. The sequence can be as long as needed
 * 3. The system may start a new sequence at any time, which will run in parallelly of other sequences.
 *
 * # But why is this done?
 *
 * Well think of a sequence that would require the player to kill a certain amount of people in a specific time.
 * The code of this sequence could be something like this:
 *
 * ```
 * awaitOwnPlayer { awaitKill() }
 * var count = 1
 * withTimeout(5.minutes) {
 *     while (count < 5) {
 *         awaitOwnPlayer { awaitKill() }
 *         count++
 *     }
 * }
 * ```
 *
 * Seams like nothing is wrong, right?
 *
 * Now take this run:
 *
 * **00:00** the player kills someone
 * **01:20** the player kills someone
 * **02:32** the player kills someone
 * **03:40** the player kills someone
 * **05:04** the player kills someone
 * **06:00** the player kills someone
 *
 * The sequence started at 00:00 with the first kill, then the player killed 3 more people in 5 minutes, but the fourth is 4 seconds too late.
 * But the player killed a sixth person at 06:00, which would have we started with the second kill at 01:20, we would have killed 5 people in 4 minutes and 40 seconds,
 * which would complete the sequence and awarded the player the advancement.
 *
 * ## The fix?
 *
 * The system looks at the first part in the sequence, if the first part is completed, the system will start the sequence again,
 * so now there are two sequences running in parallel, the first sequence is already in the withTimeout block, but the second sequence is still waiting for the first kill.
 *
 * With this, it allows us to make much more complex sequences, without thinking about to manually handle overlapping time windows or restarting the sequence whenever a new event occurs.
 *
 * ### Custom logic
 *
 * If you have custom logic, you need to use [awaitCustomLogic] around it, so that the system can detect what the first action is.
 * Only need to use this on the first action of the sequence. **This might change in the future**
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