package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import kotlinx.serialization.json.*
import java.util.*

/**
 * An advancement that is earned by doing a sequence of actions, described by a [runner].
 *
 * ```
 * object FastVentKillAdvancement : SequencedAdvancement("fast_vent_kill") {
 *     override val runner = createRunner {
 *         val exit = step("exit_vent") { awaitOwn<VentExitEvent>() }
 *         withTimeout(10.seconds) {
 *             step("kill") { awaitOwnKill() }
 *             step("enter_vent") { awaitOwn<VentEnterEvent> { it.vent.uuid == exit.vent.uuid } }
 *         }
 *     }
 * }
 * ```
 *
 * Every step needs the language key `advancement.<id>.part.<key>` (unless it is `hidden` or has a custom `languageKey`).
 *
 * @see AdvancementSequence
 * @see AdvancementSequenceRunner
 */
@ExperimentalAdvancementApi
abstract class SequencedAdvancement(override val id: String) :
    Advancement<SequencedAdvancement, SequencedAdvancement.AssignedSequencedAdvancement> {

    abstract val runner: AdvancementSequenceRunner

    /**
     * Whether attempts are started for [player], checked before every new attempt.
     * Return false if the player can not earn this advancement with their current role.
     */
    open fun applicableTo(player: HumanAmongUsPlayer): Boolean = true

    override fun new(player: UUID) = AssignedSequencedAdvancement(this, player)

    override fun createOf(
        player: UUID,
        data: JsonElement
    ): AssignedSequencedAdvancement {
        val jsonObject = data as? JsonObject
            ?: return AssignedSequencedAdvancement(this, player)
        val attempts = (jsonObject["attempts"] as? JsonPrimitive)?.intOrNull ?: 0
        val best = (jsonObject["best_attempt"] as? JsonObject)?.let { best ->
            BestAttempt(
                completedSteps = (best["completed_steps"] as? JsonArray)
                    ?.mapNotNullTo(linkedSetOf()) { (it as? JsonPrimitive)?.contentOrNull }
                    ?: emptySet(),
                failedStep = (best["failed_step"] as? JsonPrimitive)?.contentOrNull,
                timestamp = (best["timestamp"] as? JsonPrimitive)?.longOrNull ?: 0L,
            )
        }
        val failures = (jsonObject["failures"] as? JsonObject)
            ?.mapNotNull { entry -> (entry.value as? JsonPrimitive)?.intOrNull?.let { entry.key to it } }
            ?.toMap(mutableMapOf())
            ?: mutableMapOf()
        return AssignedSequencedAdvancement(this, player, attempts, best, failures)
    }

    /**
     * The furthest a single attempt got.
     *
     * @property completedSteps the keys of the steps that attempt completed
     * @property failedStep the key of the step the attempt failed in, `null` if unknown
     * @property timestamp when the attempt ended (epoch millis)
     */
    data class BestAttempt(val completedSteps: Set<String>, val failedStep: String?, val timestamp: Long)

    class AssignedSequencedAdvancement(
        override val definition: SequencedAdvancement,
        override val player: UUID,
        attempts: Int = 0,
        bestAttempt: BestAttempt? = null,
        private val failures: MutableMap<String, Int> = mutableMapOf(),
    ) : AssignedAdvancement<SequencedAdvancement, AssignedSequencedAdvancement> {

        @Suppress("OVERRIDE_DEPRECATION")
        override var isCompleted: Boolean = false

        /** How many attempts got past their first action. */
        var attempts: Int = attempts
            private set

        var bestAttempt: BestAttempt? = bestAttempt
            private set

        /** How often an attempt failed in the step with the given key. */
        fun failuresOf(stepKey: String): Int = failures[stepKey] ?: 0

        /**
         * Records a finished attempt.
         *
         * @param counts false if the attempt did not really fail (e.g. the game ended), it can then only improve [bestAttempt]
         */
        fun recordAttempt(completedSteps: Set<String>, failedStep: String?, counts: Boolean, timestamp: Long = System.currentTimeMillis()) {
            if (counts) {
                attempts++
                if (failedStep != null) failures[failedStep] = failuresOf(failedStep) + 1
            }
            val known = definition.runner.allSteps.mapTo(mutableSetOf()) { it.key }
            val progress = completedSteps.count { it in known }
            val bestProgress = bestAttempt?.completedSteps?.count { it in known } ?: -1
            if (progress > bestProgress && (progress > 0 || failedStep != null)) {
                bestAttempt = BestAttempt(completedSteps.toSet(), failedStep.takeIf { counts }, timestamp)
            }
        }

        override fun toJson(): JsonElement {
            if (attempts == 0 && bestAttempt == null && failures.isEmpty()) return JsonNull
            return buildJsonObject {
                if (attempts > 0) put("attempts", attempts)
                bestAttempt?.let { best ->
                    putJsonObject("best_attempt") {
                        putJsonArray("completed_steps") { best.completedSteps.forEach { add(it) } }
                        best.failedStep?.let { put("failed_step", it) }
                        put("timestamp", best.timestamp)
                    }
                }
                if (failures.isNotEmpty()) putJsonObject("failures") {
                    for ([key, count] in failures) put(key, count)
                }
            }
        }
    }
}
