package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.events.AmongUsEvent
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

/**
 * Base class of every exception the sequence system uses to end (a part of) a sequence in an **expected** way.
 *
 * These exceptions are thrown very often (every failed attempt ends with one), so they don't collect a stack trace.
 * Every other exception that ends a sequence is treated as a bug and logged as an error.
 *
 * @author Fantamomo
 * @since 2.5-SNAPSHOT
 */
abstract class SequenceCancellationException(message: String?) : CancellationException(message) {
    override fun fillInStackTrace(): Throwable = this
}

/**
 * Thrown by [AdvancementSequence.fail], the attempt failed outside a specific step.
 */
class SequenceFailedException(val reason: String?) :
    SequenceCancellationException(reason ?: "The sequence failed")

/**
 * Thrown by [AdvancementSequence.withTimeout] (and `step(timeoutSeconds = ...)`) when the time ran out.
 */
class SequenceTimeoutException internal constructor(
    val duration: Duration,
    internal val origin: Any,
) : SequenceCancellationException("Timed out after $duration")

/**
 * Thrown by [forbid] (or any [AdvancementSequence.withListener] handler) when a forbidden event happened.
 */
class GuardViolationException(val event: AmongUsEvent) :
    SequenceCancellationException("Forbidden event ${event::class.simpleName} happened")

/**
 * Used to cancel branches that are no longer needed, e.g. the losers of a [race] or the siblings of a failed [allOf] branch.
 */
class BranchCancelledException internal constructor(reason: String) : SequenceCancellationException(reason)

/**
 * The whole sequence was stopped from the outside (game end, advancement completed by another attempt, ...).
 */
class SequenceStoppedException internal constructor() : SequenceCancellationException("The sequence was stopped")
