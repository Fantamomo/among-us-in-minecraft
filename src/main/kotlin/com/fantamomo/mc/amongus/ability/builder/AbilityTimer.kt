package com.fantamomo.mc.amongus.ability.builder

import com.fantamomo.mc.amongus.util.Cooldown
import kotlin.time.Duration

class AbilityTimer(
    val id: String,
    internal val handle: Cooldown
) {
    var resetAfterMeeting: Boolean = true
    constructor(id: String, duration: Duration) : this(id, Cooldown(duration))

    fun start(newDuration: Duration = handle.startDuration()) {
        handle.set(newDuration)
        handle.start()
    }

    fun pause() {
        handle.pause()
    }

    fun resume() {
        handle.resume()
    }

    fun stop() {
        handle.stop()
    }

    fun reset(start: Boolean = false) {
        handle.reset(start = start)
    }

    fun isRunning(): Boolean = handle.isRunning()

    fun isFinished(): Boolean = handle.isFinished()

    fun remaining(): Duration = handle.remaining()

    fun onFinish(listener: () -> Unit) {
        handle.onFinish(listener)
    }
}
