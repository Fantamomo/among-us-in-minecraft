package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.events.AmongUsEvent
import com.fantamomo.mc.amongus.events.PlayerKillEvent
import com.fantamomo.mc.amongus.events.VentEnterEvent
import com.fantamomo.mc.amongus.events.VentExitEvent
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.util.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.Continuation
import kotlin.coroutines.RestrictsSuspension
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
 */
@RestrictsSuspension
class AdvancementSequence(
    val advancement: SequencedAdvancement,
    val player: HumanAmongUsPlayer,
    private val job: Job,

    private val scheduleTimeout: (
        Duration,
        () -> Unit
    ) -> TimeoutHandle,

    private val cancelEventWait: (Continuation<*>) -> Unit
) {

    private class TimeoutFrame {
        var expired = false
    }

    private class PendingWait(
        val continuation: Continuation<*>,
        val timeoutFrame: TimeoutFrame?
    )

    private val timeoutStack = mutableListOf<TimeoutFrame>()

    private var pendingWait: PendingWait? = null

    fun cancel(cause: Throwable) {
        val wait = pendingWait ?: return

        pendingWait = null

        cancelEventWait(wait.continuation)
        wait.continuation.resumeWith(Result.failure(cause))
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

    private suspend inline fun <reified E : AmongUsEvent> awaitEvent(): E {
        ensureActive()

        val event = player.game.eventManager.awaitEvent<E> { continuation ->
            pendingWait = PendingWait(
                continuation = continuation,
                timeoutFrame = timeoutStack.lastOrNull()
            )
        }

        pendingWait = null

        ensureActive()

        return event
    }

    fun ensureActive() {
        job.ensureActive()
    }

    suspend fun awaitEnterVent(): VentEnterEvent = awaitEvent()

    suspend fun awaitExitVent(): VentExitEvent = awaitEvent()

    suspend fun awaitKill(): PlayerKillEvent = awaitEvent()

    // MORE EVENTS
}