package com.fantamomo.mc.amongus.statistics

import kotlinx.serialization.Transient
import kotlinx.serialization.json.*

class ListStatistic(
    override val id: String
) : Statistic {
    val data: MutableList<Long> = mutableListOf()

    val min: Long get() = data.min()
    val max: Long get() = data.max()

    val average: Double get() = data.average()

    fun isEmpty() = data.isEmpty()

    fun add(element: Long) = data.add(element)

    @Transient
    private var startTime: Long? = null

    fun timerStart(time: Long = System.currentTimeMillis()) {
        startTime = time
    }

    fun timerStop(time: Long = System.currentTimeMillis()): Boolean {
        val start = startTime ?: return false
        add(time - start)
        startTime = null
        return true
    }

    override fun toJson() = buildJsonObject {
        put("type", JsonPrimitive("list"))
        put("data", JsonArray(data.map { JsonPrimitive(it) }))
    }

    companion object {
        fun fromJson(id: String, json: JsonObject): ListStatistic {
            val statistic = ListStatistic(id)
            json["data"]?.jsonArray?.forEach { statistic.add(it.jsonPrimitive.long) }
            return statistic
        }
    }
}