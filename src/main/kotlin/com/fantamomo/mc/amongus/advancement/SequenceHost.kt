package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.events.AmongUsEvent
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import kotlin.time.Duration

/**
 * Everything an [AdvancementSequence] needs from the outside world.
 *
 * The real implementation (in [AdvancementManager]) uses the game's event manager and the Bukkit scheduler,
 * tests can use a fake implementation with a virtual clock.
 *
 * All callbacks must be invoked on the same thread that runs the sequences (the server thread).
 *
 * @author Fantamomo
 * @since 2.5-SNAPSHOT
 */
interface SequenceHost {
    /** The owner of the sequence. */
    val player: HumanAmongUsPlayer

    /**
     * Calls [handler] for every event of exactly [type] (subclasses are not delivered, like in the event manager)
     * until the returned registration is cancelled.
     */
    fun <E : AmongUsEvent> listen(type: Class<E>, handler: (E) -> Unit): Registration

    /** Calls [action] once after [delay]. */
    fun schedule(delay: Duration, action: () -> Unit): Registration

    /** Calls [action] every [period] until the registration is cancelled. */
    fun repeat(period: Duration, action: () -> Unit): Registration
}

/**
 * Handle of an event listener or a timer, cancelling it must be idempotent.
 */
fun interface Registration {
    fun cancel()
}
