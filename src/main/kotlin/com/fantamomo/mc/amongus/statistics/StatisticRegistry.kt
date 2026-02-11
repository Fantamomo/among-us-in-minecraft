package com.fantamomo.mc.amongus.statistics

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.reflect.KClass

object StatisticRegistry {

    data class StatisticCreator<T : Statistic>(val type: KClass<T>, val create: (id: String, json: JsonObject) -> T)

    private val loaders: MutableMap<String, StatisticCreator<*>> = mutableMapOf()

    fun <T : Statistic> register(typeId: String, type: KClass<T>, creator: (String, JsonObject) -> T) {
        require(typeId.isNotBlank()) { "typeId must not be blank" }
        if (loaders.containsKey(typeId)) error("Statistic type $typeId already registered")
        loaders[typeId] = StatisticCreator(type, creator)
    }

    inline fun <reified T : Statistic> register(typeId: String, noinline creator: (String, JsonObject) -> T) {
        register(typeId, T::class, creator)
    }

    fun fromJson(id: String, json: JsonObject): Statistic {
        val type = json["type"]?.jsonPrimitive?.content
            ?: error("Invalid statistic data for $id: missing 'type' in $json")

        val creator = loaders[type]
            ?: error("Unknown statistic type: $type (registered: ${loaders.keys.sorted()})")

        return creator.create(id, json)
    }

    fun toJson(value: Statistic): JsonObject {
        val data = value.toData()
        val type = value::class
        val id = loaders.entries.firstOrNull { it.value.type == type }?.key
            ?: error("Unknown statistic type: ${value::class}")
        data["type"] = JsonPrimitive(id)
        return JsonObject(data)
    }
}
