package com.fantamomo.mc.amongus.advancement

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.color
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.languages.component
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

        private const val PROGRESS_BAR_LENGTH = 20
        private const val PROGRESS_BAR_CHAR = "|"
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
        inv.setItem(4, buildSummaryItem(advancementsList, player.locale()))

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

    private fun buildSummaryItem(
        advancementsList: List<Pair<Advancement<*, *>, AssignedAdvancement<*, *>>>,
        locale: Locale
    ): ItemStack {
        val completed = advancementsList.count { it.second.isCompleted }
        val total = advancementsList.size
        val item = ItemType.NETHER_STAR.createItemStack()
        item.setData(
            DataComponentTypes.CUSTOM_NAME,
            Component.translatable("advancement.ui.title")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
                .translateTo(locale)
        )
        val lore = listOf(
            textComponent {
                translatable("advancement.ui.summary") {
                    args {
                        numeric("current", completed)
                        numeric("total", total)
                    }
                }
            }.decoration(TextDecoration.ITALIC, false).translateTo(locale),
            progressBar(completed, total)
        )
        item.setData(DataComponentTypes.LORE, ItemLore.lore(lore))
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TOOLTIP_DISPLAY)
        return item
    }

    private fun progressBar(current: Int, max: Int): Component {
        val ratio = if (max <= 0) 1.0 else (current.toDouble() / max).coerceIn(0.0, 1.0)
        val filled = (ratio * PROGRESS_BAR_LENGTH).toInt()
        val percent = (ratio * 100).toInt()
        return Component.text()
            .append(Component.text(PROGRESS_BAR_CHAR.repeat(filled), NamedTextColor.GREEN))
            .append(Component.text(PROGRESS_BAR_CHAR.repeat(PROGRESS_BAR_LENGTH - filled), NamedTextColor.DARK_GRAY))
            .append(Component.text(" $percent%", NamedTextColor.GRAY))
            .decoration(TextDecoration.ITALIC, false)
            .build()
    }

    private fun Component.loreLine(locale: Locale, color: NamedTextColor? = null): Component {
        val styled = if (color != null) color(color) else this
        return styled.decoration(TextDecoration.ITALIC, false).translateTo(locale)
    }

    @OptIn(ExperimentalAdvancementApi::class)
    private fun buildAdvancementItem(
        advancement: Advancement<*, *>,
        assigned: AssignedAdvancement<*, *>,
        locale: Locale
    ): ItemStack {
        val isCompleted = assigned.isCompleted
        val isHidden = advancement.isSecret && !isCompleted
        val item = (if (isHidden) ItemType.GRAY_DYE else advancement.icon).createItemStack()

        val nameColor = if (isCompleted) NamedTextColor.GOLD else NamedTextColor.WHITE
        val name = if (isHidden) Component.translatable("advancement.ui.secret.name") else advancement.name
        item.setData(DataComponentTypes.CUSTOM_NAME, name.loreLine(locale, nameColor))

        if (isCompleted) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
        }

        val lore = mutableListOf<Component>()

        // Type line
        val typeKey = when (advancement) {
            is SequencedAdvancement -> "advancement.ui.type.sequenced"
            is ProgressiveAdvancement<*, *> -> "advancement.ui.type.progressive"
            else -> "advancement.ui.type.single"
        }
        lore.add(Component.translatable(typeKey).loreLine(locale, NamedTextColor.DARK_GRAY))

        // Status line
        val progressive = assigned as? ProgressiveAdvancement.AssignedProgressiveAdvancement<*, *>
        val sequenced = assigned as? SequencedAdvancement.AssignedSequencedAdvancement
        val statusKey = when {
            isCompleted -> "advancement.ui.status.completed"
            progressive != null && progressive.current > 0 -> "advancement.ui.status.in_progress"
            sequenced != null && sequenced.startedParts.isNotEmpty() -> "advancement.ui.status.in_progress"
            else -> "advancement.ui.status.incomplete"
        }
        lore.add(Component.translatable(statusKey).loreLine(locale))

        // Progress
        if (progressive != null && !isHidden) {
            val max = (advancement as? ProgressiveAdvancement<*, *>)?.max ?: 1
            val current = if (isCompleted) max else progressive.current
            lore.add(
                textComponent {
                    translatable("advancement.ui.progress") {
                        args {
                            numeric("current", current)
                            numeric("max", max)
                        }
                    }
                }.loreLine(locale)
            )
            lore.add(progressBar(current, max))
        }

        if (sequenced != null && !isHidden) {
            val partIds = sequenced.definition.runner.partIds
            val startedParts = sequenced.startedParts
            val completedParts = sequenced.completedParts

            lore.add(
                textComponent {
                    translatable("advancement.ui.steps") {
                        args {
                            val completedSize = completedParts.size
                            val startedSize = startedParts.size
                            val totalSize = partIds.size
                            numeric("completed", completedSize)
                            numeric("started", startedSize - completedSize)
                            numeric("untouched", totalSize - startedSize)
                        }
                    }
                }.loreLine(locale)
            )
            for ([index, id] in partIds.withIndex()) {
                lore.add(textComponent {
                    translatable("advancement.ui.steps.part") {
                        args {
                            numeric("index", index + 1)
                            component("explanation") {
                                // TODO: replace this with the real language key from the part
                                translatable("advancement.${advancement.id}.part.$id") {
                                    color(
                                        when (id) {
                                            in completedParts -> NamedTextColor.GREEN
                                            in startedParts -> NamedTextColor.YELLOW
                                            else -> NamedTextColor.GRAY
                                        }
                                    )
                                }
                            }
                        }
                    }
                }.loreLine(locale))
            }
        }

        lore.add(Component.empty())

        // Description / What is needed to complete
        val description = if (isHidden) Component.translatable("advancement.ui.secret.description") else advancement.description
        val descLines = splitLinesPreserveStyles(description.translateTo(locale)).map {
            it.color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        }
        lore.addAll(descLines)

        if (advancement.hasGameAdvantage && !isCompleted && !isHidden) {
            lore.add(Component.empty())
            lore.add(Component.translatable("advancement.ui.awarded_after_game").loreLine(locale, NamedTextColor.DARK_GRAY))
        }

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
