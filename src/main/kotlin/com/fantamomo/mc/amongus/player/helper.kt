package com.fantamomo.mc.amongus.player

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun AmongUsPlayer.editStatistics(block: PlayerStatistics.() -> Unit) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    statistics.block()
}