package com.fantamomo.mc.amongus.advancement

@ExperimentalAdvancementApi
class AdvancementSequenceRunner(val runner: suspend AdvancementSequence.() -> Unit)

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
 * @author Fantamomo
 * @since 2.4-SNAPSHOT
 */
@ExperimentalAdvancementApi
fun SequencedAdvancement.createRunner(runner: suspend AdvancementSequence.() -> Unit) = AdvancementSequenceRunner(runner)