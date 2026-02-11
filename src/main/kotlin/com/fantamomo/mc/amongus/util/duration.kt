package com.fantamomo.mc.amongus.util

import kotlin.time.Duration

fun Duration.toSmartString(): String {
    if (this.inWholeMilliseconds == 0L) return "0s"

    val abs = this.absoluteValue
    val sign = if (this.isNegative()) "-" else ""

    var remainder = abs.inWholeNanoseconds

    val days = remainder / 86_400_000_000_000
    remainder %= 86_400_000_000_000

    val hours = remainder / 3_600_000_000_000
    remainder %= 3_600_000_000_000

    val minutes = remainder / 60_000_000_000
    remainder %= 60_000_000_000

    val seconds = remainder / 1_000_000_000
    remainder %= 1_000_000_000

    val milliseconds = remainder / 1_000_000
    remainder %= 1_000_000

    val microseconds = remainder / 1_000
    remainder %= 1_000

    val nanoseconds = remainder

    val parts = buildList {
        if (days > 0) add("${days}d")
        if (hours > 0) add("${hours}h")
        if (minutes > 0) add("${minutes}m")
        if (seconds > 0) add("${seconds}s")
        if (milliseconds > 0) add("${milliseconds}ms")
        if (microseconds > 0) add("${microseconds}µs")
        if (nanoseconds > 0) add("${nanoseconds}ns")
    }

    return sign + parts.joinToString(" ")
}
