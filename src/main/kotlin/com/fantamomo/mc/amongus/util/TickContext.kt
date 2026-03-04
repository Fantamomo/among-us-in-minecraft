package com.fantamomo.mc.amongus.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@JvmInline
value class TickContext(val ticks: Long) {

    @OptIn(ExperimentalContracts::class)
    inline fun every(ticks: Long, block: () -> Unit) {
        contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
        if (isBy(ticks)) block()
    }

    fun isBy(ticks: Long) = this.ticks % ticks == 0L
}