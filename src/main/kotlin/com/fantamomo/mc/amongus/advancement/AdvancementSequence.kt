package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.amongus.advancement.utils.step.StepGroupDeclaration
import com.fantamomo.mc.amongus.advancement.utils.step.StepInfo
import com.fantamomo.mc.amongus.events.AmongUsEvent
import com.fantamomo.mc.amongus.player.HumanAmongUsPlayer
import java.util.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.RestrictsSuspension
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.startCoroutine
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The scope every runner of a [SequencedAdvancement] runs in.
 *
 * A runner describes the actions a player has to do, in order, as plain sequential code:
 *
 * ```
 * step("exit_vent") { awaitOwn<VentExitEvent>() }
 * withTimeout(10.seconds) {
 *     step("kill") { awaitOwnKill() }
 *     step("enter_vent") { awaitOwn<VentEnterEvent>() }
 * }
 * ```
 *
 * If the runner returns normally, the advancement is awarded. If it throws, the attempt failed
 * (use [fail], `stepFailed()`, timeouts or guards for that) and the system starts a new attempt.
 *
 * # Building blocks
 *
 * | What                         | How                                                                    |
 * |------------------------------|------------------------------------------------------------------------|
 * | wait for an event            | [awaitEvent], [awaitEvents], [awaitOwn], typed helpers like [awaitKill] |
 * | wait for a condition / time  | [awaitCondition], [delay]                                              |
 * | time limits                  | [withTimeout], [withTimeoutOrNull], `step(timeoutSeconds = ...)`       |
 * | "X must not happen meanwhile"| [forbid], [forbidOwn], [withListener]                                  |
 * | several things at once       | [allOf] (all branches, any order), [race] (first finished branch wins) |
 * | named parts for the GUI      | `step("key") { ... }`, `branch("key") { ... }`                          |
 * | fail the attempt             | [fail], `stepFailed()`                                                  |
 *
 * # First action and parallel attempts
 *
 * As soon as the **first action** of an attempt is done, the system starts another attempt of the same runner,
 * so overlapping time windows are handled automatically (see [AdvancementSequenceRunner] for the reason).
 *
 * The first action is the first *await region* (any `await*` call, [delay], [allOf], [race], [awaitOwnPlayer],
 * [awaitCustomLogic], ...) that completes while no other await region is open. Nested awaits count as one
 * action: `awaitOwnPlayer { awaitKill() }` is done when the player's kill arrives, not on the first kill.
 *
 * # Cancellation
 *
 * Everything is structured: [withTimeout], [forbid] and [allOf]/[race] open scopes, and cancelling a scope
 * (timeout, forbidden event, lost race, game end) cancels everything inside it. The waiting code is resumed with
 * the cause, so `finally` blocks run and listeners/timers are always removed.
 *
 * There is no `kotlinx.coroutines` involved: a sequence is a set of plain suspend functions that are resumed
 * synchronously by events and timers on the server thread.
 *
 * @author Fantamomo
 * @since 2.4-SNAPSHOT
 */
@RestrictsSuspension
@ExperimentalAdvancementApi
@OptIn(ExperimentalContracts::class)
class AdvancementSequence internal constructor(
    val runner: AdvancementSequenceRunner,
    private val host: SequenceHost,
    private val callbacks: SequenceCallbacks = SequenceCallbacks.None,
) {
    val advancement: SequencedAdvancement get() = runner.advancement

    /** The owner of this sequence. */
    val player: HumanAmongUsPlayer get() = host.player

    private var rootFiber: SequenceFiber? = null
    private val fibers = mutableListOf<SequenceFiber>()

    /** The fiber whose code is currently executing, see [runAs]. */
    private var runningFiber: SequenceFiber? = null
    private var openRegions = 0

    /** True as soon as the first action of this attempt is done. */
    var isFirstActionCompleted = false
        private set

    /** True if this attempt suspended at least once (a runner that never suspends would restart forever). */
    var hasSuspended = false
        private set

    var isFinished = false
        private set

    /** The result of this attempt, `null` while running. */
    var result: Result<Unit>? = null
        private set

    /** The keys of all steps that completed in this attempt, in completion order. */
    val completedSteps: Set<String>
        field = linkedSetOf<String>()

    /** The innermost step every exception passed through (by identity, canceled branches have their own exceptions). */
    private val stepFailures = IdentityHashMap<Throwable, StepInfo>()

    /** The innermost step this attempt failed in, `null` if it did not fail in a step (or did not fail at all). */
    val failedStep: StepInfo?
        get() = result?.exceptionOrNull()?.let { stepFailures[it] }

    // ------------------------------------------------------------------------------------------------------------
    // Lifecycle (used by the SequenceSupervisor)
    // ------------------------------------------------------------------------------------------------------------

    internal fun start() {
        check(rootFiber == null) { "The sequence was already started" }
        rootFiber = launchFiber(null, runner.runner) { result ->
            isFinished = true
            this.result = result
            callbacks.onFinished(this, result)
        }
    }

    /** Cancels this attempt, the runner is resumed with a [SequenceStoppedException]. */
    internal fun stop() {
        rootFiber?.rootScope?.cancel(SequenceStoppedException())
    }

    /** What every fiber of this sequence is currently doing, for logs and debugging. */
    fun describe(): String = if (fibers.isEmpty()) "finished ($result)" else fibers.joinToString()

    override fun toString() = "AdvancementSequence(${describe()})"

    private fun <T> launchFiber(
        parent: SequenceScope?,
        block: suspend AdvancementSequence.() -> T,
        onDone: (Result<T>) -> Unit,
    ): SequenceFiber {
        val fiber = SequenceFiber(this, parent)
        fibers += fiber
        runAs(fiber) {
            block.startCoroutine(this, Continuation(EmptyCoroutineContext) { result ->
                fiber.finish()
                fibers -= fiber
                onDone(result)
            })
        }
        return fiber
    }

    /**
     * Runs [block] (which starts or resumes the coroutine of [fiber]) with [fiber] as the running fiber.
     * The coroutine runs synchronously until it suspends or finishes, then the previous fiber is restored.
     */
    internal fun runAs(fiber: SequenceFiber, block: () -> Unit) {
        val previous = runningFiber
        runningFiber = fiber
        try {
            block()
        } finally {
            runningFiber = previous
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Core primitives
    // ------------------------------------------------------------------------------------------------------------

    private fun currentFiber(): SequenceFiber =
        runningFiber ?: error("Not running inside an advancement sequence")

    /** Throws the cancellation cause if the current part of the sequence was canceled. */
    fun ensureActive() {
        currentFiber().scope.cancellation()?.let { throw it }
    }

    /**
     * Suspends the current fiber until the [Suspension] is resumed. [setup] registers whatever resumes it
     * (bind listeners/timers to the suspension, they are removed automatically).
     */
    internal suspend fun <T> suspendFiber(setup: (Suspension<T>) -> Unit): T {
        contract {
            callsInPlace(setup, InvocationKind.EXACTLY_ONCE)
            returnsResultOf(setup)
        }
        return suspendCoroutineUninterceptedOrReturn { continuation ->
            val fiber = currentFiber()
            fiber.scope.cancellation()?.let { throw it }
            val suspension = Suspension<T>(fiber, fiber.scope)
            try {
                setup(suspension)
            } catch (e: Throwable) {
                suspension.dispose()
                throw e
            }
            val result = suspension.install(continuation)
            if (result === COROUTINE_SUSPENDED) hasSuspended = true
            result
        }
    }

    /**
     * Marks [block] as an await region, see "First action" on [AdvancementSequence].
     */
    internal inline fun <R> region(part: AdvancementSequencePart.Blocking, block: () -> R): R {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
            returnsResultOf(block)
        }
        val fiber = enterRegion(part)
        var completed = false
        try {
            val result = block()
            completed = true
            return result
        } finally {
            exitRegion(fiber, completed)
        }
    }

    internal fun enterRegion(part: AdvancementSequencePart.Blocking): SequenceFiber {
        val fiber = currentFiber()
        fiber.scope.cancellation()?.let { throw it }
        fiber.parts += part
        openRegions++
        return fiber
    }

    internal fun exitRegion(fiber: SequenceFiber, completed: Boolean) {
        fiber.parts.removeLastOrNull()
        openRegions--
        if (completed && openRegions == 0 && !isFirstActionCompleted) {
            isFirstActionCompleted = true
            callbacks.onFirstAction(this)
        }
    }

    /**
     * Runs [block] in a new child scope of the current fiber. [setup] may bind listeners/timers to the scope
     * or cancel it, they live exactly as long as [block] runs.
     */
    internal suspend fun <R> withScope(
        part: AdvancementSequencePart?,
        setup: (SequenceScope) -> Unit,
        block: suspend AdvancementSequence.(SequenceScope) -> R,
    ): R {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
            callsInPlace(setup, InvocationKind.EXACTLY_ONCE)
            returnsResultOf(block)
        }
        val fiber = currentFiber()
        fiber.scope.cancellation()?.let { throw it }
        val parent = fiber.scope
        val scope = SequenceScope(parent, fiber)
        fiber.scope = scope
        if (part != null) fiber.parts += part
        try {
            setup(scope)
            val result = block(scope)
            // the block swallowed the cancellation of its own scope, that does not count as success
            scope.cancelCause?.let { throw it }
            return result
        } finally {
            if (part != null) fiber.parts.removeLastOrNull()
            fiber.scope = parent
            scope.close()
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Steps
    // ------------------------------------------------------------------------------------------------------------

    /** Runtime part of `step(...)` and `branch("key")`, tracks the step for the GUI and applies its timeout. */
    internal suspend fun <R> withStep(info: StepInfo, block: suspend AdvancementSequence.() -> R): R {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
            returnsResultOf(block)
        }
        val fiber = currentFiber()
        fiber.scope.cancellation()?.let { throw it }
        fiber.parts += AdvancementSequencePart.NamedPart(info)
        try {
            val result = if (info.timeoutSeconds > 0) withTimeout(info.timeoutSeconds.seconds, block) else block()
            completedSteps += info.key
            return result
        } catch (e: Throwable) {
            // only the innermost step a failure passes through is the failed one
            stepFailures.putIfAbsent(e, info)
            throw e
        } finally {
            fiber.parts.removeLastOrNull()
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Awaiting
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Waits for the next event of exactly [type] that matches [filter].
     *
     * Events that don't match are ignored, the sequence keeps waiting. The filter runs inside the event dispatch,
     * exceptions thrown by it (e.g. [fail]) fail the sequence.
     */
    suspend fun <E : AmongUsEvent> awaitEvent(type: Class<E>, filter: (E) -> Boolean = { true }): E =
        awaitEvents0(listOf(type), filter)

    suspend inline fun <reified E : AmongUsEvent> awaitEvent(noinline filter: (E) -> Boolean = { true }): E =
        awaitEvent(E::class.java, filter)

    /**
     * Waits for the next event of any of the [types] that matches [filter], whichever comes first.
     *
     * ```
     * when (val event = awaitEvents(PlayerKillEvent::class, MeetingCallEvent::class)) { ... }
     * ```
     */
    suspend fun <E : AmongUsEvent> awaitEvents(vararg types: Class<out E>, filter: (E) -> Boolean = { true }): E =
        awaitEvents0(types.asList(), filter)

    suspend fun <E : AmongUsEvent> awaitEvents(vararg types: KClass<out E>, filter: (E) -> Boolean = { true }): E =
        awaitEvents0(types.map { it.java }, filter)

    private suspend fun <E : AmongUsEvent> awaitEvents0(types: List<Class<out E>>, filter: (E) -> Boolean): E {
        require(types.isNotEmpty()) { "Can not await events without any event type" }
        val distinct = types.distinct()
        return region(AdvancementSequencePart.Blocking.AwaitingEvents(distinct)) {
            suspendFiber { suspension ->
                val handler: (AmongUsEvent) -> Unit = handler@{ event ->
                    if (suspension.isDone) return@handler
                    @Suppress("UNCHECKED_CAST")
                    event as E
                    val accepted = try {
                        filter(event)
                    } catch (e: Throwable) {
                        suspension.resume(Result.failure(e))
                        return@handler
                    }
                    if (accepted) suspension.resume(Result.success(event))
                }
                for (type in distinct) {
                    @Suppress("UNCHECKED_CAST")
                    suspension.register(host.listen(type as Class<AmongUsEvent>, handler))
                }
            }
        }
    }

    /**
     * Waits until [condition] is true. It is checked immediately and then every [checkEvery].
     *
     * Use this for state that has no event, e.g. positions. Prefer events whenever possible.
     */
    suspend fun awaitCondition(checkEvery: Duration = 50.milliseconds, condition: () -> Boolean) {
        require(checkEvery.isPositive()) { "checkEvery must be positive (was $checkEvery)" }
        region(AdvancementSequencePart.Blocking.AwaitingCondition) {
            if (!condition()) suspendFiber { suspension ->
                suspension.register(host.repeat(checkEvery) {
                    if (suspension.isDone) return@repeat
                    val fulfilled = try {
                        condition()
                    } catch (e: Throwable) {
                        suspension.resume(Result.failure(e))
                        return@repeat
                    }
                    if (fulfilled) suspension.resume(Result.success(Unit))
                })
            }
        }
    }

    /** Waits for [duration]. */
    suspend fun delay(duration: Duration) {
        region(AdvancementSequencePart.Blocking.Delay(duration)) {
            if (duration.isPositive()) suspendFiber { suspension ->
                suspension.register(host.schedule(duration) { suspension.resume(Result.success(Unit)) })
            }
        }
    }

    /** Waits until the sequence is canceled (it never returns normally). */
    suspend fun awaitInfinity(): Nothing {
        region(AdvancementSequencePart.Blocking.AwaitingInfinity) {
            suspendFiber<Unit> { }
        }
        error("unreachable")
    }

    // ------------------------------------------------------------------------------------------------------------
    // Scopes: timeouts and guards
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Runs [block] and fails with a [SequenceTimeoutException] if it takes longer than [duration].
     */
    suspend fun <T> withTimeout(duration: Duration, block: suspend AdvancementSequence.() -> T): T {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
            returnsResultOf(block)
        }
        return timeout(duration, Any(), block)
    }

    /**
     * Like [withTimeout], but returns `null` instead of failing when **this** timeout runs out.
     */
    suspend fun <T> withTimeoutOrNull(duration: Duration, block: suspend AdvancementSequence.() -> T): T? {
        val origin = Any()
        return try {
            timeout(duration, origin, block)
        } catch (e: SequenceTimeoutException) {
            if (e.origin !== origin) throw e
            null
        }
    }

    private suspend fun <T> timeout(duration: Duration, origin: Any, block: suspend AdvancementSequence.() -> T): T {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
            returnsResultOf(block)
        }
        return withScope(AdvancementSequencePart.Timeout(duration), { scope ->
            val exception = SequenceTimeoutException(duration, origin)
            if (duration.isPositive()) scope.register(host.schedule(duration) { scope.cancel(exception) })
            else scope.cancel(exception)
        }) { block() }
    }

    /**
     * Runs [block] while [handler] is called for every event of [type].
     *
     * If [handler] throws (e.g. [fail] or a [GuardViolationException]), [block] is canceled with that exception.
     * This is the base of [forbid], but can also be used to collect information in the background:
     *
     * ```
     * var vents = 0
     * withListener(VentEnterEvent::class, { if (it.player == player) vents++ }) {
     *     step("kill") { awaitOwnKill() }
     * }
     * ```
     */
    suspend fun <E : AmongUsEvent, R> withListener(
        type: KClass<E>,
        handler: (E) -> Unit,
        block: suspend AdvancementSequence.() -> R,
    ): R {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
            returnsResultOf(block)
        }
        return withScope(AdvancementSequencePart.Guard(type.java), { scope ->
            scope.register(host.listen(type.java) { event ->
                if (scope.isClosed || !scope.isActive) return@listen
                try {
                    handler(event)
                } catch (e: Throwable) {
                    scope.cancel(e)
                }
            })
        }) { block() }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Parallel branches
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Runs all branches at the same time and waits until **every** branch completed, in any order.
     * If one branch fails, the others are canceled and [allOf] fails with that exception.
     *
     * Returns the result of [block], use the [Branch] handles to get the results of the branches:
     *
     * ```
     * val (kill, sabotage) = allOf {
     *     branch("kill") { awaitOwnKill() } to branch("sabotage") { awaitSabotageStart { it.player == player } }
     * }
     * kill.result.victim
     * ```
     *
     * Named branches are steps (shown as `2a.`, `2b.`, ... in the GUI).
     */
    @StepGroupDeclaration(requireAll = true)
    suspend fun <R> allOf(block: AllOfScope.() -> R): R {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
            returnsResultOf(block)
        }
        val scope = AllOfScope(this)
        val result = scope.block()
        runBranches(scope.branches, requireAll = true)
        return result
    }

    /**
     * Runs all branches at the same time, the first branch that **completes** wins and the others are canceled.
     *
     * A branch that fails (e.g. `stepFailed()` or a timeout) only drops out of the race, the race fails
     * when every branch failed. Unexpected exceptions (bugs) fail the race immediately.
     *
     * ```
     * val killedBy = race {
     *     branch("by_vote") { awaitMeetingEnd { it.ejectedPlayer == target }; "vote" }
     *     branch("by_knife") { awaitOwnKill { it.victim == target }; "knife" }
     * }
     * ```
     */
    @StepGroupDeclaration(requireAll = false)
    suspend fun <T> race(block: RaceScope<T>.() -> Unit): T {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
            returnsResultOf(block)
        }
        val scope = RaceScope<T>(this)
        scope.block()
        @Suppress("UNCHECKED_CAST")
        return runBranches(scope.branches, requireAll = false) as T
    }

    private suspend fun runBranches(branches: List<Branch<*>>, requireAll: Boolean): Any? {
        require(branches.isNotEmpty()) { "At least one branch is required" }
        return region(AdvancementSequencePart.Blocking.Parallel(requireAll, branches.size)) {
            withScope(null, {}) { groupScope ->
                suspendFiber { suspension -> BranchGroup(groupScope, branches, requireAll, suspension).start() }
            }
        }
    }

    private inner class BranchGroup(
        private val scope: SequenceScope,
        private val branches: List<Branch<*>>,
        private val requireAll: Boolean,
        private val suspension: Suspension<Any?>,
    ) {
        private val fibers = mutableListOf<SequenceFiber>()
        private var remaining = branches.size
        private var resolved = false

        fun start() {
            for ([index, branch] in branches.withIndex()) {
                if (resolved || !scope.isActive) break
                fibers += launchFiber(scope, branch.body()) { result -> onDone(index, result) }
            }
        }

        private fun <T> Branch<T>.body(): suspend AdvancementSequence.() -> T {
            val step = step ?: return block
            return { withStep(step, block) }
        }

        private fun onDone(index: Int, result: Result<Any?>) {
            // when the group scope was canceled, the cancellation resumes the parent fiber itself
            if (resolved || !scope.isActive) return
            remaining--
            val exception = result.exceptionOrNull()
            when {
                exception == null -> {
                    branches[index].complete(result.getOrNull())
                    if (!requireAll) resolve(result, "Another branch won the race")
                    else if (remaining == 0) resolve(Result.success(Unit), "")
                }
                // in a race, an expected failure only eliminates the branch
                !requireAll && exception is SequenceCancellationException && remaining > 0 -> {}
                else -> resolve(Result.failure(exception), "A sibling branch failed")
            }
        }

        private fun resolve(result: Result<Any?>, cancelReason: String) {
            resolved = true
            for (fiber in fibers) {
                if (!fiber.isFinished) fiber.rootScope.cancel(BranchCancelledException(cancelReason))
            }
            suspension.resume(result)
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Failing
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Fails the current attempt (or the current branch of a [race]).
     *
     * Inside a step, prefer `stepFailed()`, it reads better. Both work the same way.
     */
    fun fail(reason: String? = null): Nothing = throw SequenceFailedException(reason)
}

/**
 * Callbacks for the owner of a running [AdvancementSequence].
 */
@ExperimentalAdvancementApi
internal interface SequenceCallbacks {
    fun onFirstAction(sequence: AdvancementSequence) {}

    fun onFinished(sequence: AdvancementSequence, result: Result<Unit>) {}

    object None : SequenceCallbacks
}
