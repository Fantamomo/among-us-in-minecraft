package com.fantamomo.mc.amongus.manager

import com.fantamomo.mc.amongus.events.AmongUsEvent
import com.fantamomo.mc.amongus.game.Game
import org.slf4j.LoggerFactory
import kotlin.coroutines.Continuation
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class EventManager(val game: Game) {
    private val listeners: MutableMap<Class<out AmongUsEvent>, MutableList<(AmongUsEvent) -> Unit>> = mutableMapOf()
    private val markAsDeleted = mutableSetOf<(AmongUsEvent) -> Unit>()

    fun <E : AmongUsEvent> register(clazz: Class<E>, handler: (E) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        val eventHandler = handler as (AmongUsEvent) -> Unit
        listeners.getOrPut(clazz) { mutableListOf() }.add(eventHandler)
    }

    fun unregister(handler: (AmongUsEvent) -> Unit) {
        listeners.values.forEach { it.remove(handler) }
        for (deleted in markAsDeleted) {
            listeners.values.forEach { it.remove(deleted) }
        }
    }

    inline fun <reified E : AmongUsEvent> register(noinline handler: (E) -> Unit) {
        register(E::class.java, handler)
    }

    suspend fun <E : AmongUsEvent> awaitEvent(clazz: Class<E>): E {
        return suspendCoroutineUninterceptedOrReturn { continuation ->
            val callback = SuspendCallback(continuation)
            register(clazz, callback)
            COROUTINE_SUSPENDED
        }
    }

    suspend inline fun <reified E : AmongUsEvent> awaitEvent() = awaitEvent(E::class.java)

    fun call(event: AmongUsEvent) {
        listeners[event::class.java]?.forEach {
            try {
                it(event)
            } catch (e: Exception) {
                logger.error("Error while calling event handler $it with $event", e)
            }
        }
    }

    fun onGameStop() {
        listeners.values.forEach { listeners ->
            listeners.forEach {
                (it as? SuspendCallback<*>)?.stop()
            }
            listeners.clear()
        }
        listeners.clear()
    }

    private inner class SuspendCallback<E : AmongUsEvent>(val continuation: Continuation<E>) : (E) -> Unit {
        override fun invoke(event: E) {
            continuation.resume(event)
            @Suppress("UNCHECKED_CAST")
            markAsDeleted.add(this as (AmongUsEvent) -> Unit)
        }

        fun stop() {
            continuation.resumeWithException(CancellationException("Game stopped"))
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EventManager::class.java)
    }
}