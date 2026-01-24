package com.fantamomo.mc.amongus.statistics

import kotlinx.serialization.Serializable

@Serializable
class ListStatistic(
    override val id: String
) : Statistic {
    val data: MutableList<Int> = mutableListOf()

    val min: Int get() = data.min()
    val max: Int get() = data.max()

    val average: Double get() = data.average()

    fun isEmpty() = data.isEmpty()

    fun add(element: Int) = data.add(element)

}