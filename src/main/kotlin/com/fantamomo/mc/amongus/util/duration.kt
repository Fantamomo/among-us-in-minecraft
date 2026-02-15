package com.fantamomo.mc.amongus.util

import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private val UNITS = DurationUnit.entries.asReversed().map {
    it to 1.toDuration(it).inWholeNanoseconds
}

private val DurationUnit.suffix
    get() = when (this) {
        DurationUnit.DAYS -> "d"
        DurationUnit.HOURS -> "h"
        DurationUnit.MINUTES -> "m"
        DurationUnit.SECONDS -> "s"
        DurationUnit.MILLISECONDS -> "ms"
        DurationUnit.MICROSECONDS -> "µs"
        DurationUnit.NANOSECONDS -> "ns"
    }

fun Duration.toSmartString(minUnit: DurationUnit = DurationUnit.NANOSECONDS): String {
    if (this.inWholeMilliseconds == 0L) return "0s"

    val abs = this.absoluteValue
    val sign = if (this.isNegative()) "-" else ""

    var remainder = abs.inWholeNanoseconds

    val parts = buildList {
        for ((unit, factor) in UNITS) {
            if (unit.ordinal < minUnit.ordinal) continue

            val value = remainder / factor
            if (value > 0) {
                add("${value}${unit.suffix}")
                remainder %= factor
            }
        }
    }

    return sign + parts.joinToString(" ")
}