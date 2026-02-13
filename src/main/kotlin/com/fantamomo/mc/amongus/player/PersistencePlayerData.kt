package com.fantamomo.mc.amongus.player

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlin.uuid.Uuid

class PersistencePlayerData(val uuid: Uuid, private val initData: JsonObject?) {
    val helpPreferences = create("helpPreferences") { PlayerHelpPreferences.fromJsonElement(it) } ?: PlayerHelpPreferences()

    private fun <T : Any> create(id: String, loaded: (JsonElement) -> T) = try {
        initData?.get(id)?.let(loaded)
    } catch (e: Exception) {
        null
    }

    fun toJson(): JsonObject = buildJsonObject {
        put("helpPreferences", helpPreferences.toJsonElement())
    }
}