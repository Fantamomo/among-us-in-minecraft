package com.fantamomo.mc.amongus.statistics

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface Statistic {
    fun toJson(): JsonObject

    val id: String

    companion object {
        fun fromJson(id: String, json: JsonObject): Statistic {
            val type = json["type"]?.jsonPrimitive?.content ?: error("Invalid statistic data for $id: $json")
            return when (type) {
                "average" -> AverageStatistic.fromJson(id, json)
                "list" -> ListStatistic.fromJson(id, json)
                "timer" -> TimerStatistic.fromJson(id, json)
                "counter" -> CounterStatistic.fromJson(id, json)
                else -> error("Invalid statistic type: $type")
            }
        }
    }
}