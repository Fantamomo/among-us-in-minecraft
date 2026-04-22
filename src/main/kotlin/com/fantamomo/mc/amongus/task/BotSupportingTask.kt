package com.fantamomo.mc.amongus.task

import com.fantamomo.mc.amongus.player.BotAmongUsPlayer
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

interface BotSupportingTask {
    fun getTaskDurationForBot(): BotTaskDuration

    fun interface BotTaskDuration {

        fun getDuration(bot: BotAmongUsPlayer): Duration

        companion object {
            fun exact(duration: Duration) = Exact(duration)
            fun range(min: Duration, max: Duration) = InRange(min, max)
            fun range(min: Long, max: Long, unit: DurationUnit) = range(min.toDuration(unit), max.toDuration(unit))
            fun percentage(duration: Duration, percentage: Double): Percentage = Percentage(duration, percentage)
            fun percentage(duration: Duration, percentage: Int): Percentage {
                require(percentage in 0..100) { "Percentage must be between 0 and 100" }
                return percentage(duration, percentage / 100.0)
            }

            fun custom(provider: BotTaskDuration) = provider
        }

        data class Exact(val duration: Duration) : BotTaskDuration {
            override fun getDuration(bot: BotAmongUsPlayer): Duration = duration
        }

        data class InRange(val min: Duration, val max: Duration) : BotTaskDuration {

            init {
                require(min <= max) { "Min duration must be less than or equal to max duration" }
            }

            override fun getDuration(bot: BotAmongUsPlayer): Duration = min + (max - min) * Random.nextDouble()
        }

        data class Percentage(val duration: Duration, val percentage: Double) :
            BotTaskDuration by InRange(duration * percentage, duration * (1 - percentage)) {
            init {
                require(percentage in 0.0..1.0) { "Percentage must be between 0 and 1" }
            }
        }
    }
}