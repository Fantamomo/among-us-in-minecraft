package com.fantamomo.mc.amongus.settings

import com.fantamomo.mc.adventure.text.*
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.task.GuiAssignedTask
import com.fantamomo.mc.amongus.util.splitLinesPreserveStyles
import com.fantamomo.mc.amongus.util.textComponent
import com.fantamomo.mc.amongus.util.translateTo
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ItemLore
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class SettingsInventory(
    val owner: AmongUsPlayer,
    private val group: SettingsGroup? = null
) : InventoryHolder {

    private val settings = owner.game.settings
    private lateinit var inv: Inventory

    companion object {
        val SETTINGS_NAMESPACED_KEY = NamespacedKey(AmongUs, "settings/key")
        val SETTINGS_GROUP_KEY = NamespacedKey(AmongUs, "settings/group")
        val SETTINGS_BACK_KEY = NamespacedKey(AmongUs, "settings/back")
    }

    override fun getInventory(): Inventory {
        buildInventory()
        return inv
    }

    private fun buildInventory() {
        val content = group?.keys?.toList() ?: buildMainContent()

        val size = requiredSize(content.size)
        inv = Bukkit.createInventory(
            this,
            size,
            if (group == null) Component.translatable("setting.ui.title")
            else Component.translatable(group.displayName)
        )

        val borderSlots = GuiAssignedTask.getBorderItemSlots(size)
        val middleSlots = GuiAssignedTask.getMiddleItemSlots(size)

        val background = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
        borderSlots.forEach { inv.setItem(it, background) }

        if (group == null) setupMainMenu(middleSlots)
        else setupGroupMenu(middleSlots)

        if (group != null) addBackButton(size)
    }

    private fun requiredSize(contentAmount: Int): Int {
        val withBorder = contentAmount + 9
        val rows = ((withBorder / 9.0).toInt() + 1).coerceAtLeast(3)
        return (rows * 9).coerceAtMost(54)
    }

    private fun buildMainContent(): List<Any> {
        val groups = SettingsKey.groups
        val ungrouped = SettingsKey.keys().filter { it.group == null }
        return groups + ungrouped
    }

    @Suppress("UnstableApiUsage")
    private fun setupMainMenu(middleSlots: List<Int>) {
        val content = buildMainContent()
        content.forEachIndexed { index, entry ->
            val slot = middleSlots.getOrNull(index) ?: return@forEachIndexed
            when (entry) {
                is SettingsGroup -> {
                    val item = ItemStack(entry.material)
                    item.setData(
                        DataComponentTypes.ITEM_NAME,
                        Component.translatable(entry.displayName).translateTo(owner.locale)
                    )
                    val lines = splitLinesPreserveStyles(
                        Component.translatable(entry.displayDescription).translateTo(owner.locale).decoration(TextDecoration.ITALIC, false)
                    )
                    val lore = ItemLore.lore(lines)
                    item.setData(DataComponentTypes.LORE, lore)
                    item.editPersistentDataContainer {
                        it.set(SETTINGS_GROUP_KEY, PersistentDataType.STRING, entry.name)
                    }
                    inv.setItem(slot, item)
                }

                is SettingsKey<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val key = entry as SettingsKey<Any, *>
                    val value = settings[key]
                    val item = key.type.itemRepresentation(value)
                    finishItem(key, value, item)
                    inv.setItem(slot, item)
                }
            }
        }
    }

    private fun setupGroupMenu(middleSlots: List<Int>) {
        group!!.keys.forEachIndexed { index, keyRaw ->
            @Suppress("UNCHECKED_CAST")
            val key = keyRaw as SettingsKey<Any, *>
            val value = settings[key]
            val item = key.type.itemRepresentation(value)
            finishItem(key, value, item)

            val slot = middleSlots.getOrNull(index) ?: return@forEachIndexed
            inv.setItem(slot, item)
        }
    }

    private fun addBackButton(size: Int) {
        val back = ItemStack(Material.ARROW)
        back.editMeta {
            it.displayName(Component.translatable("setting.ui.back").translateTo(owner.locale))
        }
        back.editPersistentDataContainer {
            it.set(SETTINGS_BACK_KEY, PersistentDataType.BOOLEAN, true)
        }
        inv.setItem(size - 5, back)
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
        val empty = lore.isEmpty()
        key.settingsDescription?.let {
            if (!empty) lore.addFirst(Component.empty())
            lore.addFirst(Component.translatable(it).translateTo(owner.locale))
        }
        if (empty) lore.add(Component.empty())
        lore.add(textComponent {
            translatable("setting.ui.default") {
                args { string("value", key.type.stringRepresentation(key.defaultValue)) }
            }
        })
        val components = lore
            .map { it.translateTo(owner.locale) }
            .flatMap(::splitLinesPreserveStyles)
            .map { it.decoration(TextDecoration.ITALIC, false) }

        item.editPersistentDataContainer {
            it.set(SETTINGS_NAMESPACED_KEY, PersistentDataType.STRING, key.key)
        }

        item.setData(DataComponentTypes.LORE, ItemLore.lore(components))
    }

    internal fun onClick(event: InventoryClickEvent) {
        val item = event.currentItem ?: return

        if (item.persistentDataContainer.has(SETTINGS_BACK_KEY)) {
            owner.player?.openInventory(SettingsInventory(owner).inventory)
            return
        }

        val groupName = item.persistentDataContainer.get(SETTINGS_GROUP_KEY, PersistentDataType.STRING)
        if (groupName != null) {
            val target = SettingsKey.groups.find { it.name == groupName } ?: return
            owner.player?.openInventory(SettingsInventory(owner, target).inventory)
            return
        }

        val keyName = item.persistentDataContainer.get(SETTINGS_NAMESPACED_KEY, PersistentDataType.STRING) ?: return
        val settingsKey = SettingsKey.fromKey(keyName) ?: return

        @Suppress("UNCHECKED_CAST")
        val casted = settingsKey as SettingsKey<Any, *>

        if (event.click == ClickType.DROP) {
            settings.remove(casted)
        } else {
            val current = settings[casted]
            val new = casted.type.onItemClick(current, event.click)
            settings.set(casted, new)
        }

        owner.player?.openInventory(SettingsInventory(owner, group).inventory)
    }
}