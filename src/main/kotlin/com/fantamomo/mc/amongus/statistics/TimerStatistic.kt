package com.fantamomo.mc.amongus.statistics

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.time.Duration.Companion.milliseconds

@Serializable
class TimerStatistic(
    override val id: String
) : Statistic {

    var totalMillis: Long = 0
        private set

    @Transient
    private var runningSince: Long? = null

    fun start(timestamp: Long = System.currentTimeMillis()) {
        runningSince = timestamp
    }

    fun stop(timestamp: Long = System.currentTimeMillis()): Boolean {
        val start = runningSince ?: return false
        totalMillis += (timestamp - start)
        runningSince = null
        return true
    }

    fun toDuration() = totalMillis.milliseconds

    fun reset() {
        totalMillis = 0
        runningSince = null
    }
}
