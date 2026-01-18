package com.fantamomo.mc.amongus.util

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

open class Cooldown {

    private var durationMillis: Long = 0L
    private var startTime: Long = 0L
    private var remainingMillis: Long = 0L

    private var running = false
    private var paused = false
    private var finished = false

    private var tickCondition: () -> Boolean = { true }

    private val finishActions: MutableList<() -> Unit> = mutableListOf(::onFinish)

    constructor(duration: Duration, start: Boolean = false) {
        set(duration)
        if (start) start()
    }

    init {
        CooldownManager.register(this)
    }

    fun forceFinish() {
        finish()
    }

    fun set(duration: Duration) {
        durationMillis = duration.toLong(DurationUnit.MILLISECONDS)
        remainingMillis = durationMillis
        finished = false
    }

    fun reset(stop: Boolean = true, start: Boolean = false) {
        if (stop) stop()
        remainingMillis = durationMillis
        finished = false
        if (start) start()
    }

    fun start() {
        if (running || durationMillis <= 0) return

        startTime = System.currentTimeMillis()
        remainingMillis = durationMillis
        running = true
        paused = false
        finished = false
    }

    fun stop() {
        running = false
        paused = false
        remainingMillis = 0L
    }

    fun pause() {
        if (!running || paused) return

        remainingMillis -= System.currentTimeMillis() - startTime
        paused = true
        running = false
    }

    fun resume() {
        if (!paused || remainingMillis <= 0) return

        startTime = System.currentTimeMillis()
        running = true
        paused = false
    }

    fun isFinished() = finished
    fun isRunning() = running
    fun isPaused() = paused

    internal fun update() {
        if (!running || finished) return

        if (!tickCondition()) {
            startTime = System.currentTimeMillis() // todo: change it so that it does NOT reset the cooldown
            return
        }

        val elapsed = System.currentTimeMillis() - startTime
        if (elapsed >= remainingMillis) {
            finish()
        }
    }

    private fun finish() {
        running = false
        paused = false
        finished = true

        finishActions.forEach { it.invoke() }
    }

    protected open fun onFinish() {}

    fun onFinish(action: () -> Unit) {
        finishActions.add(action)
    }

    fun tickCondition(condition: () -> Boolean) {
        this.tickCondition = condition
    }

    fun startDuration(): Duration = durationMillis.milliseconds

    fun remaining(): Duration {
        return when {
            finished -> Duration.ZERO
            running -> {
                val elapsed = System.currentTimeMillis() - startTime
                (remainingMillis - elapsed).coerceAtLeast(0)
                    .toDuration(DurationUnit.MILLISECONDS)
            }
            else -> remainingMillis.toDuration(DurationUnit.MILLISECONDS)
        }
    }
}
