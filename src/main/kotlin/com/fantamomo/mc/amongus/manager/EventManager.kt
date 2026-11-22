package com.fantamomo.mc.amongus.manager

import com.fantamomo.mc.amongus.events.AmongUsEvent
import com.fantamomo.mc.amongus.game.Game
import org.slf4j.LoggerFactory

class EventManager(val game: Game) {
    private val listeners: MutableMap<Class<out AmongUsEvent>, MutableList<(AmongUsEvent) -> Unit>> = mutableMapOf()

    private var gameStopped = false

    fun <E : AmongUsEvent> register(clazz: Class<E>, handler: (E) -> Unit) {
        if (gameStopped) return
        @Suppress("UNCHECKED_CAST")
        val eventHandler = handler as (AmongUsEvent) -> Unit
        listeners.getOrPut(clazz) { mutableListOf() }.add(eventHandler)
    }

    fun unregister(handler: (AmongUsEvent) -> Unit) {
        if (gameStopped) return
        listeners.values.forEach { it.remove(handler) }
    }

    inline fun <reified E : AmongUsEvent> register(noinline handler: (E) -> Unit) {
        register(E::class.java, handler)
    }

    /**
     * Calls every handler registered for exactly the class of [event].
     *
     * Handlers are allowed to (un)register listeners, handlers registered during the call
     * do not receive this event.
     */
    fun call(event: AmongUsEvent) {
        if (gameStopped) throw IllegalStateException("Cannot call event $event because game is stopped")
        val eventListeners = listeners[event::class.java] ?: return
        if (eventListeners.isEmpty()) return
        // iterate over a copy, handlers are allowed to (un)register listeners
        for (handler in eventListeners.toList()) {
            try {
                handler(event)
            } catch (e: Exception) {
                logger.error("Error while calling event handler $handler with $event", e)
            }
        }
    }

    fun onGameStop() {
        gameStopped = true
        listeners.clear()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EventManager::class.java)
    }
}
