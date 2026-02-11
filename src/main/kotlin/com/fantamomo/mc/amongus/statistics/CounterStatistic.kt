package com.fantamomo.mc.amongus.statistics

import kotlinx.serialization.json.*

class CounterStatistic(override val id: String) : Statistic {
    var value: Int = 0
        private set

    fun increment() {
        value++
    }

    override fun toData(): MutableMap<String, JsonElement> = mutableMapOf("value" to JsonPrimitive(value))

    companion object {
        fun fromJson(id: String, json: JsonObject): CounterStatistic {
            val statistic = CounterStatistic(id)
            json["value"]?.jsonPrimitive?.int?.let { statistic.value = it }
            return statistic
        }
    }
}