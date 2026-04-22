package com.fantamomo.mc.amongus.util

import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.TimeSource

// copied from internet and modified by Fantamomo
class LatencyMonitor(
    private val softSensitivity: Double = 2.5,
    private val hardThresholdMs: Double = 10000.0,
    private val minSamples: Int = 10
) {

    constructor(softSensitivity: Double = 2.5, hardThreshold: Duration, minSamples: Int = 10) : this(
        softSensitivity,
        hardThresholdMs = hardThreshold.inWholeMilliseconds.toDouble(),
        minSamples = minSamples
    )

    private var count = 0L
    private var mean = 0.0
    private var m2 = 0.0
    private var hardViolations = 0L

    fun start() = Run(this, TimeSource.Monotonic.markNow())

    internal fun record(ms: Double): Result {
        count++

        val delta = ms - mean
        mean += delta / count
        val delta2 = ms - mean
        m2 += delta * delta2

        val variance = if (count > 1) m2 / count else 0.0
        val stdDev = sqrt(variance)

        val softThreshold = mean + softSensitivity * stdDev

        val isHardViolation = ms >= hardThresholdMs
        val isSoftViolation = count > minSamples && ms > softThreshold

        if (isHardViolation) hardViolations++

        return Result(
            latencyMs = ms,
            mean = mean,
            stdDev = stdDev,
            softThreshold = softThreshold,
            hardThreshold = hardThresholdMs,
            isSoftViolation = isSoftViolation,
            isHardViolation = isHardViolation,
            hardViolationCount = hardViolations
        )
    }

    class Run internal constructor(
        private val monitor: LatencyMonitor,
        private val start: TimeSource.Monotonic.ValueTimeMark
    ) {
        private var ended = false

        fun end(): Result {
            if (ended) throw IllegalStateException("Run already ended")
            ended = true
            val ms = start.elapsedNow().inWholeMilliseconds.toDouble()
            return monitor.record(ms)
        }
    }

    data class Result(
        val latencyMs: Double,
        val mean: Double,
        val stdDev: Double,
        val softThreshold: Double,
        val hardThreshold: Double,
        val isSoftViolation: Boolean,
        val isHardViolation: Boolean,
        val hardViolationCount: Long
    )
}