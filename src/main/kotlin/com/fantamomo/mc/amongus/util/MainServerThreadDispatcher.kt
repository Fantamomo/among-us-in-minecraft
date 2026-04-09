package com.fantamomo.mc.amongus.util

import ca.spottedleaf.moonrise.common.util.TickThread
import com.fantamomo.mc.amongus.AmongUs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlin.coroutines.CoroutineContext

private object MainServerThreadDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        AmongUs.server.scheduler.runTaskLater(AmongUs, block, 0L)
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = !TickThread.isTickThread()

    override fun toString() = "MainServerThreadDispatcher"
}

@Suppress("UnusedReceiverParameter")
val Dispatchers.ServerThread: CoroutineDispatcher
    get() = MainServerThreadDispatcher