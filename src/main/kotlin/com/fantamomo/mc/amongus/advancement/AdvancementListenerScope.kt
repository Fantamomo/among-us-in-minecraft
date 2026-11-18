package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.events.AmongUsEvent
import com.fantamomo.mc.amongus.events.PlayerAmongUsEvent
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import com.fantamomo.mc.amongus.statistics.CounterStatistic
import org.slf4j.LoggerFactory

/**
 * The scope in which a listener based advancement registers its listeners.
 *
 * A scope lives for exactly one game (or until the advancement is completed).
 * Everything registered through it (events, statistics, [onDispose] callbacks)
 * is automatically removed when the scope is disposed, so advancements never have to clean up by hand.
 *
 * Example:
 * ```
 * override fun AdvancementListenerScope<AssignedCounterAdvancement>.listen() {
 *     onOwn<VentEnterEvent> { increment() }
 *     mirror(player.statistics.playedGames)
 * }
 * ```
 *
 * @author Fantamomo
 * @since 2.5-SNAPSHOT
 */
class AdvancementListenerScope<AA : AssignedAdvancement<*, *>> internal constructor(
    val assigned: AA,
    val player: HumanAmongUsPlayer,
) {
    val game: Game = player.game

    private val disposers = mutableListOf<() -> Unit>()

    /**
     * False after the scope was disposed. Handlers check this, so a handler that is
     * still part of a running event dispatch does not fire after disposal.
     */
    var isActive: Boolean = true
        private set

    fun <E : AmongUsEvent> on(type: Class<E>, handler: (E) -> Unit) {
        if (!isActive) return
        val eventManager = game.eventManager
        val wrapped: (E) -> Unit = { if (isActive) handler(it) }
        eventManager.register(type, wrapped)
        @Suppress("UNCHECKED_CAST")
        onDispose { eventManager.unregister(wrapped as (AmongUsEvent) -> Unit) }
    }

    inline fun <reified E : AmongUsEvent> on(noinline handler: (E) -> Unit) = on(E::class.java, handler)

    /**
     * Like [on], but only for events where [PlayerAmongUsEvent.player] is the owner of this advancement.
     */
    inline fun <reified E : PlayerAmongUsEvent> onOwn(noinline handler: (E) -> Unit) =
        on(E::class.java) { if (it.player == player) handler(it) }

    /**
     * Calls [handler] every time [statistic] changes.
     */
    fun onChange(statistic: CounterStatistic, handler: (now: Int, prev: Int) -> Unit) {
        if (!isActive) return
        val remove = statistic.onChange { now, prev -> if (isActive) handler(now, prev) }
        onDispose(remove)
    }

    /**
     * Registers a callback that is called when the scope is disposed (game end or advancement completed).
     */
    fun onDispose(block: () -> Unit) {
        if (!isActive) {
            block()
            return
        }
        disposers.add(block)
    }

    fun award() = assigned.award()

    internal fun dispose() {
        if (!isActive) return
        isActive = false
        for (disposer in disposers.asReversed()) {
            try {
                disposer()
            } catch (e: Exception) {
                logger.error("Failed to dispose listener of advancement ${assigned.definition.id}", e)
            }
        }
        disposers.clear()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AdvancementListenerScope::class.java)
    }
}
