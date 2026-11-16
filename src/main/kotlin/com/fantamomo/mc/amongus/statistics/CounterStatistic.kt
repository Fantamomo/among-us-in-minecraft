package com.fantamomo.mc.amongus.statistics

import kotlinx.serialization.json.*

class CounterStatistic(override val id: String) : Statistic {
    private val listeners = mutableListOf<(Int, Int) -> Unit>()

    var value: Int = 0
        private set

    fun increment() {
        value++
        listeners.forEach { it(value, value - 1) }
    }

    override fun shouldSave() = value != 0

    override fun toData(): MutableMap<String, JsonElement> = mutableMapOf("value" to JsonPrimitive(value))

    fun onChange(onChange: (now: Int, prev: Int) -> Unit): () -> Unit {
        listeners.add(onChange)
        return { listeners.remove(onChange) }
    }

    companion object {
        fun fromJson(id: String, json: JsonObject): CounterStatistic {
            val statistic = CounterStatistic(id)
            json["value"]?.jsonPrimitive?.int?.let { statistic.value = it }
            return statistic
        }
    }
}