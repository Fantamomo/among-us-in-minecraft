package com.fantamomo.mc.amongus.settings

import com.fantamomo.mc.adventure.text.append
import com.fantamomo.mc.adventure.text.text
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.util.splitLinesPreserveStyles
import com.fantamomo.mc.amongus.util.textComponent
import com.fantamomo.mc.amongus.util.translateTo
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ItemLore
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class SettingsInventory(val owner: AmongUsPlayer) : InventoryHolder {
    private val inv = Bukkit.createInventory(this, 54, Component.translatable("setting.ui.title"))
    private val settings = owner.game.settings
    override fun getInventory(): Inventory {
        setupInventory()
        return inv
    }

    fun setupInventory() {
        SettingsKey.keys().forEachIndexed { index, key ->
            @Suppress("UNCHECKED_CAST")
            key as SettingsKey<Any, *>
            val value = settings[key]
            val item = key.type.itemRepresentation(value)
            finishItem(key, value, item)
            item.editPersistentDataContainer {
                it.set(SETTINGS_NAMESPACED_KEY, PersistentDataType.STRING, key.key)
            }
            inv.setItem(index, item)
        }
    }

    @Suppress("UnstableApiUsage")
    private fun <T : Any> finishItem(key: SettingsKey<T, *>, value: T, item: ItemStack) {
        item.setData(
            DataComponentTypes.ITEM_NAME,
            textComponent(owner.locale) {
                translatable(key.settingsDisplayName)
                text(": ", NamedTextColor.GRAY)
                append(key.type.componentRepresentation(value))
            }
        )
        val lore = item.getData(DataComponentTypes.LORE)?.lines()?.toMutableList() ?: mutableListOf()
        key.settingsDescription?.let {
            if (lore.isNotEmpty()) lore.add(Component.empty())
            lore.addFirst(Component.translatable(it).translateTo(owner.locale))
        }
        val components = lore
            .map { it.translateTo(owner.locale) }
            .flatMap(::splitLinesPreserveStyles)
            .map { it.decoration(TextDecoration.ITALIC, false) }

        item.setData(DataComponentTypes.LORE, ItemLore.lore(components))
    }

    internal fun onClick(event: InventoryClickEvent) {
        val currentItem = event.currentItem ?: return
        val key = currentItem.persistentDataContainer.get(SETTINGS_NAMESPACED_KEY, PersistentDataType.STRING) ?: return
        val settingsKey = SettingsKey.fromKey(key) ?: return
        @Suppress("UNCHECKED_CAST")
        settingsKey as SettingsKey<Any, *>
        val currentValue = settings[settingsKey]
        val new = settingsKey.type.onItemClick(currentValue)
        settings.set(settingsKey, new)
        setupInventory()
    }

    companion object {
        val SETTINGS_NAMESPACED_KEY = NamespacedKey(AmongUs, "settings/inventory")
    }
}