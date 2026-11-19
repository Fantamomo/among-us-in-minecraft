package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.events.AmongUsEvent
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.util.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.Continuation
import kotlin.coroutines.RestrictsSuspension
import kotlin.coroutines.resume
import kotlin.reflect.KClass
import kotlin.time.Duration


/**
 * This will be used in Advancements that require a sequence of events to be completed.
 *
 * Example: Kill a player while 2 other are watching, but survive the meeting call.
 *
 * The sequence of events could be something like this (simplified):
 * ```
 * awaitKill() // check if the kill was made by the player
 * // check if 2 crewmates are watching
 * withTimeout(10.seconds) {
 *     awaitMeetingCall() // check if the meeting call was about this corpse
 * }
 * awaitMeetingEnd() // wait for the meeting to end, check if the player survived
 * ```
 *
 * Another example:
 * "Kill 3 players in 5 minutes"
 * ```
 * awaitKill()
 * var kills = 1
 * withTimeout(5.minutes) {
 *     while (kills < 3) {
 *         awaitKill()
 *         kills++
 *     }
 * }
 * ```
 *
 * If any exception like CancellationException or TimeoutException is thrown, we ignore that and just start over.
 *
 * There are many more events that can be added and used to extend the sequence of events.
 *
 * @author Fantamomo
 * @since 2.4-SNAPSHOT
 */
@RestrictsSuspension
@ExperimentalAdvancementApi
class AdvancementSequence(
    val advancement: SequencedAdvancement,
    val player: HumanAmongUsPlayer,

    private val scheduleTimeout: (
        Duration,
        () -> Unit
    ) -> TimeoutHandle,

    private val cancelEventWait: (Continuation<*>) -> Unit,
    private val partChangeListener: PartChangeListener = PartChangeListener.Empty,
) {


    private class TimeoutFrame {
        var expired = false
    }

    private class PendingWait(
        val continuation: Continuation<*>,
        val timeoutFrame: TimeoutFrame?
    )

    private val timeoutStack = mutableListOf<TimeoutFrame>()
    private val blockerStack = mutableListOf<AdvancementSequencePart>()

    private var exception: Throwable? = null

    private var pendingWait: PendingWait? = null

    fun cancel(cause: Throwable) {
        if (exception != null) return
        exception = cause
        val wait = pendingWait ?: return

        pendingWait = null

        cancelEventWait(wait.continuation)
        wait.continuation.resumeWith(Result.failure(cause))
    }

//    fun stop() {
//        cancel(CancellationException("Advancement sequence stopped"))
//    }

    fun award() {
        if (pendingWait != null) return
        player.awardAdvancement(advancement)
    }

    @OptIn(ExperimentalContracts::class)
    internal suspend inline fun <R> withPart(
        part: AdvancementSequencePart,
        block: suspend AdvancementSequence.() -> R
    ): R {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
            returnsResultOf(block)
        }
        blockerStack.add(part)
        if (partChangeListener !== PartChangeListener.Empty) partChangeListener.onPartAdded(blockerStack, part)

        var exceptional = false

        try {
            return block()
        } catch (e: Throwable) {
            exceptional = true
            throw e
        } finally {
            val last = blockerStack.removeLastOrNull()
            if (last != null && partChangeListener !== PartChangeListener.Empty) partChangeListener.onPartRemoved(blockerStack, last, exceptional)
            if (last != part) throw IllegalStateException("Blocker stack is corrupted (expected $part, got $last)")
        }
    }

    suspend fun <T> withTimeout(
        duration: Duration,
        block: suspend AdvancementSequence.() -> T
    ): T {
        ensureActive()

        val frame = TimeoutFrame()
        timeoutStack.add(frame)

        val timeoutHandle = scheduleTimeout(duration) {
            if (frame.expired) return@scheduleTimeout

            frame.expired = true

            val wait = pendingWait ?: return@scheduleTimeout

            val frameIndex = timeoutStack.indexOf(frame)
            val waitFrameIndex = timeoutStack.indexOf(wait.timeoutFrame)

            if (wait.timeoutFrame == null || waitFrameIndex >= frameIndex) {
                cancel(TimeoutCancellationException("Advancement timed out after $duration"))
            }
        }

        try {
            ensureActive()

            return block(this)
        } finally {
            timeoutHandle.cancel()
            timeoutStack.remove(frame)
        }
    }

    suspend fun <E : AmongUsEvent> awaitEvent(type: Class<E>): E {
        ensureActive()

        @Suppress("UNCHECKED_CAST")
        val blocker = AdvancementSequencePart.Blocking.AwaitingEvent(type as Class<AmongUsEvent>)

        val event = withPart(blocker) {
            awaitEvent0<E>(type) { continuation ->
                pendingWait = PendingWait(
                    continuation = continuation,
                    timeoutFrame = timeoutStack.lastOrNull()
                )
            }
        }

        pendingWait = null

        ensureActive()

        return event
    }

    suspend fun <E : AmongUsEvent> awaitEvents(vararg types: Class<out E>): E {
        if (types.isEmpty()) throw IllegalArgumentException("Can not await Events without any event type")
        if (types.size == 1) return awaitEvent(types.first())
        val distinct = types.distinct()
        if (distinct.size == 1) return awaitEvent(distinct.first())
        return awaitEvents0(distinct)
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <E : AmongUsEvent> awaitEvents0(types: List<Class<out E>>): E {
        val blocker = AdvancementSequencePart.Blocking.AwaitingEvents(types as List<Class<AmongUsEvent>>)
        return withPart(blocker) { awaitEvent1(types) }
    }

    suspend fun <E : AmongUsEvent> awaitEvents(vararg types: KClass<out E>): E {
        if (types.isEmpty()) throw IllegalArgumentException("Can not await Events without any event type")
        if (types.size == 1) return awaitEvent(types.first().java)
        val distinct = types.distinct()
        if (distinct.size == 1) return awaitEvent(distinct.first().java)
        return awaitEvents0(types.map { it.java })
    }

    private suspend inline fun <E : AmongUsEvent> awaitEvent1(types: List<Class<out E>>): E {
        return suspendCancellableCoroutine { continuation ->
            var resumed = false
            var handler: (AmongUsEvent) -> Unit = {}
            handler = {
                if (!resumed) {
                    resumed = true
                    @Suppress("UNCHECKED_CAST")
                    continuation.resume(it as E)
                    player.game.eventManager.unregister(handler)
                }
            }

            for (type in types) {
                player.game.eventManager.register(type, handler)
            }
        }
    }

    private suspend inline fun <E : AmongUsEvent> awaitEvent0(
        type: Class<E>,
        noinline continuationHandler: ((Continuation<E>) -> Unit)
    ) = player.game.eventManager.awaitEvent(type, continuationHandler)

    suspend inline fun <reified E : AmongUsEvent> awaitEvent() = awaitEvent(E::class.java)

    suspend inline fun awaitInfinity() = suspendCancellableCoroutine<Unit> {}

    fun ensureActive() {
        if (exception != null) throw exception!!
    }
}