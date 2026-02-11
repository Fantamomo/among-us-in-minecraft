package com.fantamomo.mc.amongus.statistics

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

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

    override fun toData(): MutableMap<String, JsonElement> = mutableMapOf<String, JsonElement>().apply {
        put("numerator", JsonObject(numerator.toData()))
        put("denominator", JsonObject(denominator.toData()))
    }

    companion object {
        fun fromJson(id: String, json: JsonObject): AverageStatistic {
            val numerator = json["numerator"]?.let { TimerStatistic.fromJson(id, it.jsonObject) } ?: TimerStatistic(id)
            val denominator =
                json["denominator"]?.let { CounterStatistic.fromJson(id, it.jsonObject) } ?: CounterStatistic(id)
            val statistic = AverageStatistic(id, numerator, denominator)
            return statistic
        }
    }
}