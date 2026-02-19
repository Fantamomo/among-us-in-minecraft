package com.fantamomo.mc.amongus.statistics

import kotlinx.serialization.json.JsonElement

sealed interface Statistic {
    fun shouldSave(): Boolean
    fun toData(): MutableMap<String, JsonElement>

    val id: String
}