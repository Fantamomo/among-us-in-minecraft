package com.fantamomo.mc.amongus.util.log

import kotlin.time.Instant

class ActionEntry<A : ActionElement>(
    val type: A,
    val timestamp: Instant
)