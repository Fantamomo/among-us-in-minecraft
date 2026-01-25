package com.fantamomo.mc.amongus.statistics

import kotlinx.serialization.Serializable

@Serializable
class AverageStatistic(
    override val id: String,
    val numerator: TimerStatistic = TimerStatistic(id),
    val denominator: CounterStatistic = CounterStatistic(id)
) : Statistic {

    val value: Double
        get() = if (denominator.value == 0)
            0.0
        else
            numerator.totalMillis.toDouble() / denominator.value

    fun start(timestamp: Long = System.currentTimeMillis()) {
        numerator.start(timestamp)
    }

    fun stop(timestamp: Long = System.currentTimeMillis()): Boolean {
        if (numerator.stop(timestamp)) {
            denominator.increment()
            return true
        }
        return false
    }
}
