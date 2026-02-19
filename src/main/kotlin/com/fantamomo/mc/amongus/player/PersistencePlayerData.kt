package com.fantamomo.mc.amongus.player

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import kotlinx.serialization.json.*
import net.kyori.adventure.key.Key
import org.bukkit.inventory.meta.trim.TrimMaterial
import org.bukkit.inventory.meta.trim.TrimPattern
import kotlin.uuid.Uuid

class PersistencePlayerData(val uuid: Uuid, private val initData: JsonObject?) {
    val helpPreferences =
        create("helpPreferences") { PlayerHelpPreferences.fromJsonElement(it) } ?: PlayerHelpPreferences()

    var color: PlayerColor? = create("color") { element ->
        PlayerColor.valueOf(element.jsonPrimitive.content.uppercase())
            .takeIf { !it.restricted || uuid.toLongs { m, l -> m == -1068489508091050182 && l == -4702338907290895863 } }
    }
    var trimMaterial: TrimMaterial? = create("trim_material") {
        RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL).get(Key.key(it.jsonPrimitive.content))
    }
    var trimPattern: TrimPattern? = create("trim_pattern") {
        RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN).get(Key.key(it.jsonPrimitive.content))
    }

    private fun <T> create(id: String, loaded: (JsonElement) -> T) = try {
        initData?.get(id)?.let(loaded)
    } catch (e: Exception) {
        null
    }

    fun toJson(): JsonObject = buildJsonObject {
        put("helpPreferences", helpPreferences.toJsonElement())
        color?.let { put("color", it.name.lowercase()) }
        trimMaterial?.let { RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL).getKey(it) }
            ?.let { put("trim_material", it.toString()) }
        trimPattern?.let { RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN).getKey(it) }
            ?.let { put("trim_pattern", it.toString()) }
    }
}