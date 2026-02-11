package com.fantamomo.mc.amongus.statistics

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.Uuid

class StatisticMap(val group: String, val id: Uuid) {

    private val statistics: MutableMap<String, Statistic> = mutableMapOf()

    init {
        checkGroup(group)
    }

    fun getKeys(): Set<String> = statistics.keys

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

        fun fromJson(group: String, id: Uuid, json: JsonObject): StatisticMap {
            val map = StatisticMap(group, id)

            for ((key, value) in json) {
                if (value !is JsonObject) {
                    StatisticsManager.logger.warn("Invalid statistic data for $key of $group/$id: $value")
                    continue
                }
                try {
                    map.statistics[key] = StatisticRegistry.fromJson(key, value)
                } catch (e: Exception) {
                    StatisticsManager.logger.warn("Failed to load statistic $key of $group/$id", e)
                }
            }

            return map
        }
    }

    fun toJson(): JsonObject {
        val data: MutableMap<String, JsonElement> = mutableMapOf()
        for (entry in statistics) {
            data[entry.key] = StatisticRegistry.toJson(entry.value)
        }
        return JsonObject(data)
    }
}