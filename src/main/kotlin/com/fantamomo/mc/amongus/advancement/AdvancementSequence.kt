package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.events.AmongUsEvent
import com.fantamomo.mc.amongus.events.PlayerKillEvent
import com.fantamomo.mc.amongus.events.VentEnterEvent
import com.fantamomo.mc.amongus.events.VentExitEvent
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.coroutines.RestrictsSuspension
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

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
class AdvancementSequence(val advancement: Advancement<*, *>, val player: HumanAmongUsPlayer) {

    private val timeoutStack: MutableList<Instant> = mutableListOf()

    private var exception: Exception? = null

    internal fun addTimeoutStart(duration: Duration) {
        val timeoutAt = Clock.System.now().plus(duration)
        timeoutStack.add(timeoutAt)
    }

    internal fun removeTimeout() {
        timeoutStack.removeFirst()
    }

    private suspend inline fun <reified E : AmongUsEvent> awaitEvent(): E {
        ensureActive()
        val event = player.game.eventManager.awaitEvent<E>()
        ensureActive()
        return event
    }

    fun ensureActive() {
        if (exception != null) {
            throw exception!!
        }
        if (timeoutStack.isNotEmpty()) {
            val now = Clock.System.now()
            for (timeoutAt in timeoutStack) {
                if (now > timeoutAt) {
                    exception = TimeoutCancellationException("Timeout reached")
                    throw exception!!
                }
            }
        }
    }

//    suspend fun awaitMeetingCall(): MeetingCallEvent {
//        TODO("Not yet implemented")
//    }
//
//    suspend fun awaitMeetingEnd(): MeetingEndEvent {
//        TODO("Not yet implemented")
//    }

    suspend fun awaitEnterVent(): VentEnterEvent = awaitEvent()

    suspend fun awaitExitVent(): VentExitEvent = awaitEvent()

    suspend fun awaitKill(): PlayerKillEvent = awaitEvent()

    // MORE EVENTS

    companion object {
        private val TimeoutCancellationExceptionConstructor = run {
            val constructor =
                kotlinx.coroutines.TimeoutCancellationException::class.java.getConstructor(String::class.java)
            constructor.isAccessible = true
            constructor
        }
        private val TimeoutCancellationException = { message: String ->
            TimeoutCancellationExceptionConstructor.newInstance(message) as TimeoutCancellationException
        }
    }
}