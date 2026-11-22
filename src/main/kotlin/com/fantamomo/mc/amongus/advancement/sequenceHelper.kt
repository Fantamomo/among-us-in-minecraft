@file:OptIn(ExperimentalContracts::class)

package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.events.AmongUsEvent
import com.fantamomo.mc.amongus.events.PlayerAmongUsEvent
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.KClass

/**
 * Waits for the next event of type [E] that was caused by the owner of the sequence and matches [filter].
 *
 * ```
 * val exit = awaitOwn<VentExitEvent>()
 * awaitOwn<VentEnterEvent> { it.vent.uuid == exit.vent.uuid }
 * ```
 */
@ExperimentalAdvancementApi
suspend inline fun <reified E : PlayerAmongUsEvent> AdvancementSequence.awaitOwn(
    noinline filter: (E) -> Boolean = { true }
): E = awaitEvent(E::class.java) { it.player == player && filter(it) }

/**
 * Repeats [block] until it returns an event of the owner of the sequence.
 *
 * Prefer [awaitOwn] or the filter of [AdvancementSequence.awaitEvent], this is kept for awaits without a filter.
 */
@ExperimentalAdvancementApi
suspend fun <E : PlayerAmongUsEvent> AdvancementSequence.awaitOwnPlayer(block: suspend AdvancementSequence.() -> E): E {
    @Suppress("WRONG_INVOCATION_KIND")
    contract {
        callsInPlace(block, InvocationKind.AT_LEAST_ONCE)
        returnsResultOf(block)
    }
    return region(AdvancementSequencePart.Blocking.AwaitingOwnPlayer) {
        var event: E
        do {
            event = block()
        } while (event.player != player)
        event
    }
}

/**
 * Marks [block] as a single action, so that the first action of a sequence can consist of several awaits.
 *
 * ```
 * step("finish_tasks") {
 *     awaitCustomLogic {
 *         while (player.tasks.any { !it.completed }) awaitTaskFinished()
 *     }
 * }
 * ```
 */
@ExperimentalAdvancementApi
suspend fun <R> AdvancementSequence.awaitCustomLogic(block: suspend AdvancementSequence.() -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        returnsResultOf(block)
    }
    return region(AdvancementSequencePart.Blocking.AwaitingCustomLogic) { block() }
}

/**
 * Repeats [provider] until [filter] accepts its result, all of it counts as one action.
 */
@ExperimentalAdvancementApi
suspend fun <T> AdvancementSequence.awaitCustomLogicFilter(
    provider: suspend AdvancementSequence.() -> T,
    filter: (T) -> Boolean
): T {
    contract {
        callsInPlace(provider, InvocationKind.AT_LEAST_ONCE)
        callsInPlace(filter, InvocationKind.AT_LEAST_ONCE)
        returnsResultOf(provider)
    }
    return region(AdvancementSequencePart.Blocking.AwaitingCustomLogic) {
        var result: T
        do {
            result = provider()
        } while (!filter(result))
        result
    }
}

/**
 * Runs [block], but fails with a [GuardViolationException] as soon as an event of [type] matching [where] happens.
 *
 * ```
 * // two kills without a meeting in between
 * step("first") { awaitOwnKill() }
 * forbid(MeetingCallEvent::class) {
 *     step("second") { awaitOwnKill() }
 * }
 * ```
 */
@ExperimentalAdvancementApi
suspend fun <E : AmongUsEvent, R> AdvancementSequence.forbid(
    type: KClass<E>,
    where: (E) -> Boolean = { true },
    block: suspend AdvancementSequence.() -> R,
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        returnsResultOf(block)
    }
    return withListener(type, { if (where(it)) throw GuardViolationException(it) }, block)
}

/**
 * Like [forbid], but only events caused by the owner of the sequence are forbidden.
 */
@ExperimentalAdvancementApi
suspend fun <E : PlayerAmongUsEvent, R> AdvancementSequence.forbidOwn(
    type: KClass<E>,
    where: (E) -> Boolean = { true },
    block: suspend AdvancementSequence.() -> R,
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        returnsResultOf(block)
    }
    return forbid(type, { it.player == player && where(it) }, block)
}
