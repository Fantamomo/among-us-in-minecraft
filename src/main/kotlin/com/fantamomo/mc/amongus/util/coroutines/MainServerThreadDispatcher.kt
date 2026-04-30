package com.fantamomo.mc.amongus.util.coroutines

import ca.spottedleaf.moonrise.common.util.TickThread
import com.fantamomo.mc.amongus.AmongUs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlin.coroutines.CoroutineContext

/**
 * @see Dispatchers.ServerThread
 */
private object MainServerThreadDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        AmongUs.server.scheduler.runTaskLater(AmongUs, block, 0L)
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = !TickThread.isTickThread()

    override fun toString() = "MainServerThreadDispatcher"
}

/**
 * Dispatcher for executing coroutine work on the Minecraft server main thread.
 *
 * All tasks are scheduled through the AmongUs plugin scheduler and therefore
 * run inside the server tick loop.
 *
 * This dispatcher should be used for Bukkit/Paper APIs that must run on the
 * main server thread.
 *
 * ## Important Notes
 *
 * - Execution is tick-based and not immediate.
 * - Tasks may run later than expected depending on server TPS and scheduler load.
 * - `delay(...)` is not guaranteed to be accurate because timing depends on ticks.
 * - All scheduled tasks are bound to the AmongUs plugin lifecycle.
 *
 * ## Plugin Lifecycle
 *
 * Tasks scheduled through this dispatcher depend on the AmongUs plugin.
 *
 * If the plugin is disabled:
 * - New tasks cannot be scheduled.
 * - Pending tasks may never be executed.
 * - Suspended coroutines waiting for this dispatcher may never resume.
 */
@Suppress("UnusedReceiverParameter")
val Dispatchers.ServerThread: CoroutineDispatcher
    get() = MainServerThreadDispatcher