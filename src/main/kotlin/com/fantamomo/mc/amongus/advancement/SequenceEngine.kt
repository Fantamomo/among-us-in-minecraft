package com.fantamomo.mc.amongus.advancement

import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

/*
 * The internals of the sequence engine. Nothing here uses kotlinx.coroutines, everything is built on plain
 * `kotlin.coroutines` continuations and runs synchronously on the thread that delivers events and timers.
 *
 * - A [SequenceFiber] is one coroutine of a sequence. The runner is the root fiber, every branch of
 *   `allOf`/`race` is a child fiber. A fiber is suspended at most at one point at a time ([SequenceFiber.suspension]).
 *   Restricted coroutines must use the empty coroutine context, so the sequence tracks which fiber is running
 *   itself: fibers are only ever started or resumed through [AdvancementSequence.runAs].
 * - A [SequenceScope] is a node in the cancellation tree. Every fiber has a root scope, `withTimeout`, `forbid`,
 *   `allOf`, ... open child scopes. Cancelling a scope cancels all scopes below it (including child fibers),
 *   disposes their listeners/timers and resumes the suspended fibers with the cancellation cause.
 * - A [Suspension] is a single suspension point (waiting for an event, a timer, child fibers, ...).
 */

@ExperimentalAdvancementApi
internal class SequenceFiber(
    val sequence: AdvancementSequence,
    parentScope: SequenceScope?,
) {
    val rootScope = SequenceScope(parentScope, this)

    /** The innermost open scope of this fiber. */
    var scope: SequenceScope = rootScope

    /** The point this fiber is currently suspended at, `null` while running or finished. */
    var suspension: Suspension<*>? = null

    /** What this fiber is currently doing, innermost last (only for debugging). */
    val parts: MutableList<AdvancementSequencePart> = mutableListOf()

    var isFinished = false
        private set

    fun finish() {
        isFinished = true
        suspension = null
        rootScope.close()
    }

    override fun toString() = parts.joinToString(" > ", prefix = "Fiber[", postfix = "]")
}

@ExperimentalAdvancementApi
internal class SequenceScope(val parent: SequenceScope?, val fiber: SequenceFiber) {
    var cancelCause: Throwable? = null
        private set

    var isClosed = false
        private set

    private var children: MutableList<SequenceScope>? = null
    private var registrations: MutableList<Registration>? = null

    init {
        parent?.let { (it.children ?: mutableListOf<SequenceScope>().also { list -> it.children = list }).add(this) }
    }

    /** False if this scope or any scope above it was cancelled. */
    val isActive: Boolean
        get() = cancellation() == null

    /** The cause of the innermost cancelled scope from here upwards, `null` if active. */
    fun cancellation(): Throwable? {
        var scope: SequenceScope? = this
        while (scope != null) {
            scope.cancelCause?.let { return it }
            scope = scope.parent
        }
        return null
    }

    fun isWithin(ancestor: SequenceScope): Boolean {
        var scope: SequenceScope? = this
        while (scope != null) {
            if (scope === ancestor) return true
            scope = scope.parent
        }
        return false
    }

    /** Binds [registration] to the lifetime of this scope. */
    fun register(registration: Registration) {
        if (isClosed || cancelCause != null) {
            registration.cancel()
            return
        }
        (registrations ?: mutableListOf<Registration>().also { registrations = it }).add(registration)
    }

    fun close() {
        if (isClosed) return
        isClosed = true
        disposeRegistrations()
        parent?.children?.remove(this)
    }

    /**
     * Cancels this scope and everything below it.
     *
     * First everything is marked as cancelled, then the suspended fibers are resumed with their cancellation cause,
     * innermost (child fibers) first, so a parent always continues after its children have finished.
     */
    fun cancel(cause: Throwable) {
        val suspended = mutableListOf<SequenceFiber>()
        markCancelled(cause, suspended)
        for (fiber in suspended) {
            val suspension = fiber.suspension ?: continue
            val cancellation = suspension.scope.cancellation() ?: continue
            suspension.resume(Result.failure(cancellation))
        }
    }

    private fun markCancelled(cause: Throwable, suspended: MutableList<SequenceFiber>) {
        if (cancelCause != null || isClosed) return
        cancelCause = cause
        disposeRegistrations()
        children?.toList()?.forEach { it.markCancelled(cause, suspended) }
        val suspension = fiber.suspension
        if (suspension != null && suspension.scope.isWithin(this) && fiber !in suspended) suspended += fiber
    }

    private fun disposeRegistrations() {
        val list = registrations ?: return
        registrations = null
        for (registration in list.asReversed()) registration.cancel()
    }
}

@ExperimentalAdvancementApi
internal class Suspension<T>(val fiber: SequenceFiber, val scope: SequenceScope) {
    private var continuation: Continuation<T>? = null
    private var registrations: MutableList<Registration>? = null
    private var earlyResult: Result<T>? = null

    var isDone = false
        private set

    /** Binds [registration] to this suspension, it is cancelled as soon as the suspension is resumed. */
    fun register(registration: Registration) {
        if (isDone) {
            registration.cancel()
            return
        }
        (registrations ?: mutableListOf<Registration>().also { registrations = it }).add(registration)
    }

    /** Resumes the fiber, ignored if already resumed. May be called before [install] (the fiber then does not suspend). */
    fun resume(result: Result<T>) {
        if (isDone) return
        isDone = true
        dispose()
        val continuation = continuation
        if (continuation == null) {
            earlyResult = result
            return
        }
        if (fiber.suspension === this) fiber.suspension = null
        fiber.sequence.runAs(fiber) { continuation.resumeWith(result) }
    }

    /** Returns the early result or [COROUTINE_SUSPENDED] after installing [continuation]. */
    fun install(continuation: Continuation<T>): Any? {
        earlyResult?.let { return it.getOrThrow() }
        this.continuation = continuation
        fiber.suspension = this
        return COROUTINE_SUSPENDED
    }

    fun dispose() {
        val list = registrations ?: return
        registrations = null
        for (registration in list.asReversed()) registration.cancel()
    }
}
