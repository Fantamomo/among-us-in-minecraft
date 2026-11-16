package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.languages.numeric
import com.fantamomo.mc.amongus.task.GuiAssignedTask
import com.fantamomo.mc.amongus.util.splitLinesPreserveStyles
import com.fantamomo.mc.amongus.util.translateTo
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ItemLore
import io.papermc.paper.datacomponent.item.TooltipDisplay
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType
import org.bukkit.persistence.PersistentDataType
import java.util.*

@Suppress("UnstableApiUsage")
class AdvancementsInventory(
    val player: Player,
    private var page: Int = 0
) : InventoryHolder {

    private lateinit var inv: Inventory

    companion object {
        val KEY_NAV_PREV = NamespacedKey(AmongUs, "advancements/prev")
        val KEY_NAV_NEXT = NamespacedKey(AmongUs, "advancements/next")

        private val SHOWN_COMPONENTS = setOf(DataComponentTypes.CUSTOM_NAME, DataComponentTypes.ITEM_NAME, DataComponentTypes.LORE)
        private val TOOLTIP_DISPLAY = TooltipDisplay.tooltipDisplay()
            .hiddenComponents(
                Registry.DATA_COMPONENT_TYPE.filterTo(mutableSetOf()) { it !in SHOWN_COMPONENTS }
            )
            .build()
        private val HIDDEN_TOOLTIP_DISPLAY = TooltipDisplay.tooltipDisplay()
            .hideTooltip(true)
            .build()
    }

    override fun getInventory(): Inventory {
        buildInventory()
        return inv
    }

    private fun buildInventory() {
        val advancementsList = AdvancementManager.getAdvancementsForPlayer(player.uniqueId)
        val middleSlots = GuiAssignedTask.getMiddleItemSlots(54)
        val pageSize = middleSlots.size
        val totalPages = maxOf(1, (advancementsList.size + pageSize - 1) / pageSize)
        if (page >= totalPages) page = totalPages - 1
        if (page < 0) page = 0

        val size = 54
        val title = Component.translatable("advancement.ui.title")
        inv = Bukkit.createInventory(this, size, title)

        val borderSlots = GuiAssignedTask.getBorderItemSlots(size)
        val background = ItemType.BLACK_STAINED_GLASS_PANE.createItemStack()
        background.setData(DataComponentTypes.TOOLTIP_DISPLAY, HIDDEN_TOOLTIP_DISPLAY)
        borderSlots.forEach { inv.setItem(it, background) }

        val startIndex = page * pageSize
        val pageItems = advancementsList.drop(startIndex).take(pageSize)

        for ([index, pair] in pageItems.withIndex()) {
            val slot = middleSlots[index]
            val item = buildAdvancementItem(pair.first, pair.second, player.locale())
            inv.setItem(slot, item)
        }

        // Navigation buttons
        if (page > 0) {
            val prevItem = ItemType.ARROW.createItemStack()
            prevItem.setData(
                DataComponentTypes.CUSTOM_NAME,
                Component.translatable("advancement.ui.previous_page")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false)
                    .translateTo(player.locale())
            )
            prevItem.setData(DataComponentTypes.TOOLTIP_DISPLAY, TOOLTIP_DISPLAY)
            prevItem.editPersistentDataContainer {
                it.set(KEY_NAV_PREV, PersistentDataType.BOOLEAN, true)
            }
            inv.setItem(45, prevItem)
        }

        if (totalPages > 1) {
            val pageIndicator = ItemType.PAPER.createItemStack()
            pageIndicator.setData(
                DataComponentTypes.CUSTOM_NAME,
                textComponent {
                    translatable("advancement.ui.page") {
                        args {
                            numeric("current", page + 1)
                            numeric("total", totalPages)
                        }
                    }
                }.color(NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false)
                    .translateTo(player.locale())
            )
            pageIndicator.setData(DataComponentTypes.TOOLTIP_DISPLAY, TOOLTIP_DISPLAY)
            inv.setItem(49, pageIndicator)
        }

        if (page < totalPages - 1) {
            val nextItem = ItemType.ARROW.createItemStack()
            nextItem.setData(
                DataComponentTypes.CUSTOM_NAME,
                Component.translatable("advancement.ui.next_page")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false)
                    .translateTo(player.locale())
            )
            nextItem.setData(DataComponentTypes.TOOLTIP_DISPLAY, TOOLTIP_DISPLAY)
            nextItem.editPersistentDataContainer {
                it.set(KEY_NAV_NEXT, PersistentDataType.BOOLEAN, true)
            }
            inv.setItem(53, nextItem)
        }
    }

    private fun buildAdvancementItem(
        advancement: Advancement<*, *>,
        assigned: AssignedAdvancement<*, *>,
        locale: Locale
    ): ItemStack {
        val isCompleted = assigned.isCompleted
        val itemType = advancement.icon
        val item = itemType.createItemStack()

        val nameColor = if (isCompleted) NamedTextColor.GOLD else NamedTextColor.WHITE
        val nameComponent = advancement.name.translateTo(locale).color(nameColor).decoration(TextDecoration.ITALIC, false)
        item.setData(DataComponentTypes.CUSTOM_NAME, nameComponent)

        if (isCompleted) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
        }

        val lore = mutableListOf<Component>()

        // Status line
        if (isCompleted) {
            lore.add(
                Component.translatable("advancement.ui.status.completed")
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false)
                    .translateTo(locale)
            )
        } else if (assigned is ProgressiveAdvancement.AssignedProgressiveAdvancement<*, *>) {
            val current = assigned.current
            val max = (advancement as? ProgressiveAdvancement<*, *>)?.max ?: 1
            val statusComp = if (current > 0) {
                Component.translatable("advancement.ui.status.in_progress")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false)
                    .translateTo(locale)
            } else {
                Component.translatable("advancement.ui.status.incomplete")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
                    .translateTo(locale)
            }
            lore.add(statusComp)
            lore.add(
                textComponent {
                    translatable("advancement.ui.progress") {
                        args {
                            numeric("current", current)
                            numeric("max", max)
                        }
                    }
                }.decoration(TextDecoration.ITALIC, false).translateTo(locale)
            )
        } else {
            lore.add(
                Component.translatable("advancement.ui.status.incomplete")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
                    .translateTo(locale)
            )
        }

        lore.add(Component.empty())

        // Description / What is needed to complete
        val descComponent = advancement.description.translateTo(locale)
        val descLines = splitLinesPreserveStyles(descComponent).map {
            it.color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        }
        lore.addAll(descLines)

        item.setData(DataComponentTypes.LORE, ItemLore.lore(lore))
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TOOLTIP_DISPLAY)

        return item
    }

    fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
        val clickedItem = event.currentItem ?: return
        val pdc = clickedItem.persistentDataContainer

        if (pdc.has(KEY_NAV_PREV, PersistentDataType.BOOLEAN)) {
            page--
            buildInventory()
            player.openInventory(inv)
        } else if (pdc.has(KEY_NAV_NEXT, PersistentDataType.BOOLEAN)) {
            page++
            buildInventory()
            player.openInventory(inv)
        }
    }
}
