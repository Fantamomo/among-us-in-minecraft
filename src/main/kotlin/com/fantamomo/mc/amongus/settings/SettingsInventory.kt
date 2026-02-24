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
import io.papermc.paper.datacomponent.item.TooltipDisplay
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
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

    @Suppress("UnstableApiUsage")
    companion object {
        val KEY_SETTINGS = NamespacedKey(AmongUs, "settings/key")
        val KEY_GROUP = NamespacedKey(AmongUs, "settings/group")
        val KEY_BACK = NamespacedKey(AmongUs, "settings/back")
        private val SHOWN_COMPONENTS = setOf(DataComponentTypes.CUSTOM_NAME, DataComponentTypes.ITEM_NAME, DataComponentTypes.LORE)
        private val TOOLTIP_DISPLAY = TooltipDisplay.tooltipDisplay()
            .hiddenComponents(
                Registry.DATA_COMPONENT_TYPE.filterTo(mutableSetOf()) { it !in SHOWN_COMPONENTS }
            )
            .build()
    }

    override fun getInventory(): Inventory {
        buildInventory()
        return inv
    }

    private fun buildInventory() {
        val content = resolveContent()
        val size = requiredSize(content.size)

        inv = Bukkit.createInventory(this, size, buildTitle())

        val borderSlots = GuiAssignedTask.getBorderItemSlots(size)
        val middleSlots = GuiAssignedTask.getMiddleItemSlots(size)

        val background = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
        borderSlots.forEach { inv.setItem(it, background) }

        populateMiddle(content, middleSlots)

        if (group != null) addBackButton(size)
    }

    private fun resolveContent(): List<Any> = when (group) {
        null -> buildList {
            addAll(SettingsKey.groups)
            addAll(SettingsKey.keys().filter { it.group == null })
        }

        else -> buildList {
            val hasSubs = group.subGroups.isNotEmpty()
            val hasKeys = group.keys.isNotEmpty()

            addAll(group.subGroups)

            if (hasSubs && hasKeys) {
                add(ItemStack(Material.GRAY_STAINED_GLASS_PANE).also {
                    it.editMeta { m -> m.displayName(Component.empty()) }
                })
            }

            addAll(group.keys)
        }
    }

    private fun populateMiddle(content: List<Any>, middleSlots: List<Int>) {
        content.forEachIndexed { index, entry ->
            val slot = middleSlots.getOrNull(index) ?: return@forEachIndexed
            val item = when (entry) {
                is SettingsGroup -> buildGroupItem(entry)
                is SettingsKey<*, *> -> buildSettingItem(entry)
                is ItemStack -> entry
                else -> return@forEachIndexed
            }
            inv.setItem(slot, item)
        }
    }

    @Suppress("UnstableApiUsage")
    private fun buildGroupItem(g: SettingsGroup): ItemStack {
        val item = ItemStack(g.material)

        val itemName = Component.translatable(g.displayName).translateTo(owner.locale)
        if (g.useCustomName) {
            item.setData(
                DataComponentTypes.CUSTOM_NAME,
                itemName.decoration(TextDecoration.ITALIC, false)
            )
        } else {
            item.setData(
                DataComponentTypes.ITEM_NAME,
                itemName
            )
        }

        val descLines = splitLinesPreserveStyles(
            Component.translatable(g.displayDescription)
                .translateTo(owner.locale)
        ).map { it.decoration(TextDecoration.ITALIC, false) }
        item.setData(DataComponentTypes.LORE, ItemLore.lore(descLines))
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TOOLTIP_DISPLAY)

        item.editPersistentDataContainer {
            it.set(KEY_GROUP, PersistentDataType.STRING, g.name)
        }

        return item
    }

    @Suppress("UnstableApiUsage")
    private fun buildSettingItem(keyRaw: SettingsKey<*, *>): ItemStack {
        @Suppress("UNCHECKED_CAST")
        val key = keyRaw as SettingsKey<Any, *>
        val value = settings[key]
        val item = key.type.itemRepresentation(value)

        item.setData(
            DataComponentTypes.ITEM_NAME,
            textComponent(owner.locale) {
                append(key.settingsDisplayName)
                text(": ", NamedTextColor.GRAY)
                append(key.type.componentRepresentation(value))
            }
        )

        val lore = item.getData(DataComponentTypes.LORE)?.lines()?.toMutableList() ?: mutableListOf()
        val hadLore = lore.isNotEmpty()

        key.settingsDescription?.let {
            if (hadLore) lore.addFirst(Component.empty())
            lore.addFirst(it.translateTo(owner.locale))
        }

        if (lore.isEmpty()) lore.add(Component.empty())
        lore.add(
            textComponent {
                translatable("setting.ui.default") {
                    args { string("value", key.type.stringRepresentation(key.defaultValue)) }
                }
            }
        )

        val finalLore = lore
            .map { it.translateTo(owner.locale) }
            .flatMap(::splitLinesPreserveStyles)
            .map { it.decoration(TextDecoration.ITALIC, false) }

        item.setData(DataComponentTypes.LORE, ItemLore.lore(finalLore))

        item.editPersistentDataContainer {
            it.set(KEY_SETTINGS, PersistentDataType.STRING, key.key)
        }

        return item
    }

    private fun addBackButton(size: Int) {
        val back = ItemStack(Material.ARROW)
        back.editMeta {
            it.displayName(Component.translatable("setting.ui.back").translateTo(owner.locale))
        }
        back.editPersistentDataContainer {
            it.set(KEY_BACK, PersistentDataType.BOOLEAN, true)
        }
        inv.setItem(size - 5, back)
    }

    internal fun onClick(event: InventoryClickEvent) {
        val item = event.currentItem ?: return

        if (item.persistentDataContainer.has(KEY_BACK)) {
            val target = group?.parent
            owner.player?.openInventory(SettingsInventory(owner, target).inventory)
            return
        }

        val groupName = item.persistentDataContainer.get(KEY_GROUP, PersistentDataType.STRING)
        if (groupName != null) {
            val target = findGroup(SettingsKey.groups, groupName) ?: return
            owner.player?.openInventory(SettingsInventory(owner, target).inventory)
            return
        }

        val keyName = item.persistentDataContainer.get(KEY_SETTINGS, PersistentDataType.STRING) ?: return
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

    private fun requiredSize(contentAmount: Int): Int {
        if (contentAmount <= 0) return 9 * 3
        val middleSlotsPerRow = 7
        val rowsNeeded = ((contentAmount + middleSlotsPerRow - 1) / middleSlotsPerRow)
        val totalRows = (rowsNeeded + 2).coerceAtMost(6)
        return totalRows * 9
    }

    private fun buildTitle(): Component = when (group) {
        null -> Component.translatable("setting.ui.title")
        else -> Component.translatable(group.displayName)
    }

    private fun findGroup(groups: List<SettingsGroup>, name: String): SettingsGroup? {
        for (g in groups) {
            if (g.name == name) return g
            val found = findGroup(g.subGroups, name)
            if (found != null) return found
        }
        return null
    }
}