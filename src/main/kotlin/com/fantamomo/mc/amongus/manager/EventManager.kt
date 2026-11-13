package com.fantamomo.mc.amongus.manager

import com.fantamomo.mc.amongus.events.AmongUsEvent
import com.fantamomo.mc.amongus.game.Game
import kotlinx.coroutines.suspendCancellableCoroutine
import org.slf4j.LoggerFactory
import kotlin.coroutines.Continuation
import kotlin.coroutines.cancellation.CancellationException
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

    internal suspend fun <E : AmongUsEvent> awaitEvent(
        clazz: Class<E>,
        continuationHandler: ((Continuation<E>) -> Unit)?
    ): E {
//        logger.info("Awaiting event of type $clazz")
        return suspendCancellableCoroutine { continuation ->
            val callback = SuspendCallback(continuation)
//            logger.info("Registered callback for event of type $clazz for $continuation")
            register(clazz, callback)

            continuationHandler?.invoke(continuation)

            continuation.invokeOnCancellation {
//                logger.info("Unregistering callback for event of type $clazz for $continuation")
                @Suppress("UNCHECKED_CAST")
                unregister(callback as SuspendCallback<AmongUsEvent>)
            }
        }
    }

    internal suspend inline fun <reified E : AmongUsEvent> awaitEvent(noinline continuationHandler: ((Continuation<E>) -> Unit)? = null) =
        awaitEvent(E::class.java, continuationHandler)

    suspend fun <E : AmongUsEvent> awaitEvent(clazz: Class<E>) = awaitEvent(clazz, null)
    suspend inline fun <reified E : AmongUsEvent> awaitEvent() = awaitEvent(E::class.java)

    fun call(event: AmongUsEvent) {
        val eventListeners = listeners[event::class.java] ?: return
        val suspendCallbacks = mutableListOf<SuspendCallback<AmongUsEvent>>()
        for (handler in eventListeners) {
            if (handler is SuspendCallback<AmongUsEvent>) {
                suspendCallbacks.add(handler)
                continue
            }
            try {
                handler(event)
            } catch (e: Exception) {
                logger.error("Error while calling event handler $handler with $event", e)
            }
        }
        suspendCallbacks.forEach { it.invoke(event) }
        eventListeners.removeAll(suspendCallbacks)

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

    fun removeListener(continuation: Continuation<*>) {
        listeners.values.forEach { listeners ->
            listeners.removeIf {
                (it as? SuspendCallback<*>)?.continuation == continuation
            }
        }
    }

    private inner class SuspendCallback<E : AmongUsEvent>(val continuation: Continuation<E>) : (E) -> Unit {
        override fun invoke(event: E) {
//            logger.info("Resumed $continuation with $event", Throwable("Just for the stacktrace"))
            continuation.resume(event)
        }

        fun stop() {
            continuation.resumeWithException(CancellationException("Game stopped"))
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EventManager::class.java)
    }
}