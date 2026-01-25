package com.fantamomo.mc.amongus.statistics

import kotlinx.serialization.Serializable

@Serializable
class CounterStatistic(override val id: String) : Statistic {
    var value: Int = 0
        private set

    fun increment() {
        value++
    }
}