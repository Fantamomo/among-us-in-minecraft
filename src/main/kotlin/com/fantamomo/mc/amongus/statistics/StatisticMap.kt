package com.fantamomo.mc.amongus.statistics

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
class StatisticMap(val group: String, val id: Uuid) {

    private val statistics: MutableMap<String, Statistic> = mutableMapOf()

    init {
        checkGroup(group)
    }

    fun get(key: String): Statistic? = statistics[key]

    @Suppress("UNCHECKED_CAST")
    private inline fun <S : Statistic> getOrCreate(key: String, factory: () -> S): S {
        val value = statistics[key] as? S
        return value ?: factory().also { statistics[key] = it }
    }

    fun average(id: String): AverageStatistic = getOrCreate(id) { AverageStatistic(id) }
    fun counter(id: String): CounterStatistic = getOrCreate(id) { CounterStatistic(id) }
    fun list(id: String): ListStatistic = getOrCreate(id) { ListStatistic(id) }
    fun timer(id: String): TimerStatistic = getOrCreate(id) { TimerStatistic(id) }

    companion object {
        private val groupRegex = Regex("^[a-zA-Z0-9_-]{1,16}$")

        fun checkGroup(group: String) {
            if (!groupRegex.matches(group)) throw IllegalArgumentException("Group name must match regex $groupRegex")
        }
    }
}