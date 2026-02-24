package com.fantamomo.mc.amongus.player

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.command.Permissions
import com.fantamomo.mc.amongus.task.GuiAssignedTask
import com.fantamomo.mc.amongus.util.RefPersistentDataType
import com.fantamomo.mc.amongus.util.hideTooltip
import com.fantamomo.mc.amongus.util.translateTo
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ItemLore
import io.papermc.paper.datacomponent.item.TooltipDisplay
import io.papermc.paper.datacomponent.item.UseCooldown
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Keyed
import org.bukkit.NamespacedKey
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType
import org.bukkit.inventory.meta.trim.ArmorTrim
import org.bukkit.inventory.meta.trim.TrimMaterial
import org.bukkit.inventory.meta.trim.TrimPattern
import org.bukkit.persistence.PersistentDataType
import java.util.*

@Suppress("UnstableApiUsage")
class WardrobeInventory private constructor(
    internal val owner: AmongUsPlayer,
    private val parent: WardrobeInventory? = null,
    private val type: Type = Type.MAIN
) : InventoryHolder {

    constructor(owner: AmongUsPlayer) : this(owner, null, Type.MAIN)

    val inv = Bukkit.createInventory(this, type.size, Component.translatable("wardrobe.title"))

    override fun getInventory(): Inventory {
        buildInventory()
        return inv
    }

    private fun buildInventory() {
        GuiAssignedTask.getBorderItemSlots(type.size).forEach { inv.setItem(it, BACKGROUND_ITEM) }
        if (parent != null) {
            val item = BACK_ITEM.createItemStack()
            item.setData(
                DataComponentTypes.ITEM_NAME,
                Component.translatable("wardrobe.back").translateTo(owner.locale)
            )
            inv.setItem(type.backSlot, item)
        }

        if (type == Type.MATERIAL || type == Type.PATTER) {
            if (owner.armorTrim != null) {
                val item = REMOVE_TRIM.createItemStack()
                item.setData(
                    DataComponentTypes.ITEM_NAME,
                    Component.translatable("wardrobe.remove_armor_trim").translateTo(owner.locale)
                )
                item.editPersistentDataContainer {
                    it.set(KEY_REMOVE_TRIM, PersistentDataType.BOOLEAN, true)
                }
                inv.setItem(type.backSlot + 1, item)
            }
        }

        when (type) {
            Type.MAIN -> buildMain()
            Type.COLOR -> buildColor()
            Type.PATTER -> buildPattern()
            Type.MATERIAL -> buildMaterial()
        }
    }

    private fun buildMain() {
        if (owner.player?.hasPermission(Permissions.SET_PLAYER_COLOR) == true) {
            val colorItem = owner.color.rawItemStack()
            colorItem.editPersistentDataContainer {
                it.set(
                    KEY_MAIN,
                    RefPersistentDataType.refPersistentDataType(),
                    RefPersistentDataType.newRef(Type.COLOR)
                )
            }
            colorItem.setData(DataComponentTypes.TOOLTIP_DISPLAY, ARMOR_HIDDEN_COMPONENT)
            colorItem.setData(DataComponentTypes.ITEM_NAME, owner.color.capitalizeColoredName)
            inv.setItem(3, colorItem)
        }
        if (owner.player?.hasPermission(Permissions.SET_PLAYER_TRIM) != true) return

        val armorTrim = owner.armorTrim
        val (materialItem, patternItem) = if (armorTrim != null) {
            val materialItem = (MATERIAL_TO_ITEM_TYPE[armorTrim.material]?.createItemStack()
                ?: throw IllegalStateException("Armor material not found for ${armorTrim.material}"))
            materialItem.setData(
                DataComponentTypes.ITEM_NAME,
                armorTrim.material.description()
            )

            val patternItem = PATTERN_TO_ITEM_TYPE[armorTrim.pattern]?.createItemStack()
                ?: throw IllegalStateException("Armor pattern not found for ${armorTrim.pattern}")
            patternItem.setData(
                DataComponentTypes.ITEM_NAME,
                armorTrim.pattern.description()
            )

            materialItem to patternItem
        } else {
            val materialItem = NOT_SET_ITEM.createItemStack()
            materialItem.setData(
                DataComponentTypes.ITEM_NAME,
                Component.translatable("wardrobe.material.not_set").translateTo(owner.locale)
            )
            val patternItem = NOT_SET_ITEM.createItemStack()
            patternItem.setData(
                DataComponentTypes.ITEM_NAME,
                Component.translatable("wardrobe.pattern.not_set").translateTo(owner.locale)
            )

            materialItem to patternItem
        }

        materialItem.editPersistentDataContainer {
            it.set(KEY_MAIN, RefPersistentDataType.refPersistentDataType(), RefPersistentDataType.newRef(Type.MATERIAL))
        }
        patternItem.editPersistentDataContainer {
            it.set(KEY_MAIN, RefPersistentDataType.refPersistentDataType(), RefPersistentDataType.newRef(Type.PATTER))
        }

        inv.setItem(4, materialItem)
        inv.setItem(5, patternItem)
    }

    private fun buildColor() {
        val middleSlots = GuiAssignedTask.getMiddleItemSlots(type.size)
        val allowRestricted = owner.uuid == UUID(-1068489508091050182, -4702338907290895863)
        var index = 0
        PlayerColor.entries.forEach { color ->
            if (color.restricted && !allowRestricted) return@forEach
            val item = color.rawItemStack()
            val lore = mutableListOf<Component>()
            if (color == owner.color) {
                item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
                lore.add(Component.translatable("wardrobe.currently_selected"))
            } else if (!owner.game.isColorFree(color)) {
                item.setData(
                    DataComponentTypes.USE_COOLDOWN,
                    UseCooldown.useCooldown(Float.MAX_VALUE / 2).cooldownGroup(color.cooldownGroup)
                )
                lore.add(Component.translatable("wardrobe.color_not_available"))
            }
            if (lore.isNotEmpty()) {
                val lines = lore.map { it.decoration(TextDecoration.ITALIC, false).translateTo(owner.locale) }
                item.setData(DataComponentTypes.LORE, ItemLore.lore().addLines(lines))
            }
            item.setData(DataComponentTypes.TOOLTIP_DISPLAY, ARMOR_HIDDEN_COMPONENT)
            item.setData(DataComponentTypes.ITEM_NAME, color.capitalizeColoredName)
            item.editPersistentDataContainer {
                it.set(KEY_COLOR, RefPersistentDataType.refPersistentDataType(), RefPersistentDataType.newRef(color))
            }
            inv.setItem(middleSlots[index], item)
            index++
        }
        for (i in index until middleSlots.size) inv.setItem(middleSlots[i], BACKGROUND_ITEM)
    }

    private fun buildPattern() {
        val middleSlots = GuiAssignedTask.getMiddleItemSlots(type.size)
        var index = 0
        PATTERN_TO_ITEM_TYPE.forEach { (pattern, itemType) ->
            val item = createItemStack(itemType, pattern, owner.armorTrim?.pattern)
            item.setData(DataComponentTypes.ITEM_NAME, pattern.description())
            item.editPersistentDataContainer {
                it.set(
                    KEY_PATTERN,
                    RefPersistentDataType.refPersistentDataType(),
                    RefPersistentDataType.newRef(pattern)
                )
            }
            inv.setItem(middleSlots[index], item)
            index++
        }
        for (i in index until middleSlots.size) inv.setItem(middleSlots[i], BACKGROUND_ITEM)
    }

    private fun buildMaterial() {
        val middleSlots = GuiAssignedTask.getMiddleItemSlots(type.size)
        var index = 0
        MATERIAL_TO_ITEM_TYPE.forEach { (material, itemType) ->
            val item = createItemStack(itemType, material, owner.armorTrim?.material)
            item.setData(DataComponentTypes.ITEM_NAME, material.description())
            item.editPersistentDataContainer {
                it.set(
                    KEY_MATERIAL,
                    RefPersistentDataType.refPersistentDataType(),
                    RefPersistentDataType.newRef(material)
                )
            }
            inv.setItem(middleSlots[index], item)
            index++
        }
        for (i in index until middleSlots.size) inv.setItem(middleSlots[i], BACKGROUND_ITEM)
    }

    private fun <K : Keyed> createItemStack(type: ItemType, keyed: K, owner: K?): ItemStack {
        val item = type.createItemStack()
        if (keyed === owner) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
            item.setData(
                DataComponentTypes.LORE,
                ItemLore.lore().addLine(
                    Component.translatable("wardrobe.currently_selected")
                        .decoration(TextDecoration.ITALIC, false)
                        .translateTo(this.owner.locale)
                )
            )
        }
        return item
    }

    fun onClick(event: InventoryClickEvent) {
        val slot = event.slot
        val parent = parent
        if (slot == type.backSlot && parent != null) {
            owner.player?.openInventory(parent.inventory)
            return
        }
        val item = event.currentItem ?: return
        val pdc = item.persistentDataContainer
        if (type == Type.MAIN) {
            if (pdc.has(KEY_MAIN)) {
                val type = pdc.get(KEY_MAIN, RefPersistentDataType.refPersistentDataType<Type>())?.getOrNull() ?: return
                owner.player?.openInventory(WardrobeInventory(owner, this, type).inventory)
            }
            return
        }
        if (pdc.has(KEY_COLOR)) {
            val color =
                pdc.get(KEY_COLOR, RefPersistentDataType.refPersistentDataType<PlayerColor>())?.getOrNull() ?: return
            if (!owner.game.isColorFree(color)) return
            owner.color = color
        } else if (pdc.has(KEY_PATTERN)) {
            val pattern =
                pdc.get(KEY_PATTERN, RefPersistentDataType.refPersistentDataType<TrimPattern>())?.getOrNull() ?: return
            owner.armorTrim = ArmorTrim(owner.armorTrim?.material ?: TrimMaterial.IRON, pattern)
        } else if (pdc.has(KEY_MATERIAL)) {
            val material =
                pdc.get(KEY_MATERIAL, RefPersistentDataType.refPersistentDataType<TrimMaterial>())?.getOrNull()
                    ?: return
            owner.armorTrim = ArmorTrim(material, owner.armorTrim?.pattern ?: TrimPattern.BOLT)
        } else if (pdc.has(KEY_REMOVE_TRIM)) {
            owner.armorTrim = null
        }
        buildInventory()
    }

    internal fun update() {
        buildInventory()
    }

    companion object {
        private val KEY_MAIN = NamespacedKey(AmongUs, "wardrobe/main")
        private val KEY_COLOR = NamespacedKey(AmongUs, "wardrobe/color")
        private val KEY_PATTERN = NamespacedKey(AmongUs, "wardrobe/pattern")
        private val KEY_MATERIAL = NamespacedKey(AmongUs, "wardrobe/material")
        private val KEY_REMOVE_TRIM = NamespacedKey(AmongUs, "wardrobe/remove_trim")

        private val BACKGROUND_ITEM = ItemType.BLACK_STAINED_GLASS_PANE.createItemStack().hideTooltip()
        private val NOT_SET_ITEM = ItemType.STRUCTURE_VOID
        private val BACK_ITEM = ItemType.ARROW
        private val REMOVE_TRIM = ItemType.BARRIER

        private val ARMOR_HIDDEN_COMPONENT = TooltipDisplay.tooltipDisplay()
            .addHiddenComponents(DataComponentTypes.DYED_COLOR, DataComponentTypes.ATTRIBUTE_MODIFIERS)
            .build()

        private val PATTERN_TO_ITEM_TYPE: Map<TrimPattern, ItemType> = mapOf(
            TrimPattern.BOLT to ItemType.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE,
            TrimPattern.COAST to ItemType.COAST_ARMOR_TRIM_SMITHING_TEMPLATE,
            TrimPattern.DUNE to ItemType.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE,
            TrimPattern.EYE to ItemType.EYE_ARMOR_TRIM_SMITHING_TEMPLATE,
            TrimPattern.FLOW to ItemType.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE,
            TrimPattern.HOST to ItemType.HOST_ARMOR_TRIM_SMITHING_TEMPLATE,
            TrimPattern.RAISER to ItemType.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE,
            TrimPattern.RIB to ItemType.RIB_ARMOR_TRIM_SMITHING_TEMPLATE,
            TrimPattern.SENTRY to ItemType.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE,
            TrimPattern.SHAPER to ItemType.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE,
            TrimPattern.SILENCE to ItemType.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE,
            TrimPattern.SNOUT to ItemType.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE,
            TrimPattern.SPIRE to ItemType.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE,
            TrimPattern.TIDE to ItemType.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE,
            TrimPattern.VEX to ItemType.VEX_ARMOR_TRIM_SMITHING_TEMPLATE,
            TrimPattern.WARD to ItemType.WARD_ARMOR_TRIM_SMITHING_TEMPLATE,
            TrimPattern.WAYFINDER to ItemType.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE,
            TrimPattern.WILD to ItemType.WILD_ARMOR_TRIM_SMITHING_TEMPLATE
        )

        private val MATERIAL_TO_ITEM_TYPE: Map<TrimMaterial, ItemType> = mapOf(
            TrimMaterial.AMETHYST to ItemType.AMETHYST_SHARD,
            TrimMaterial.COPPER to ItemType.COPPER_INGOT,
            TrimMaterial.DIAMOND to ItemType.DIAMOND,
            TrimMaterial.EMERALD to ItemType.EMERALD,
            TrimMaterial.GOLD to ItemType.GOLD_INGOT,
            TrimMaterial.IRON to ItemType.IRON_INGOT,
            TrimMaterial.LAPIS to ItemType.LAPIS_LAZULI,
            TrimMaterial.NETHERITE to ItemType.NETHERITE_INGOT,
            TrimMaterial.QUARTZ to ItemType.QUARTZ,
            TrimMaterial.REDSTONE to ItemType.REDSTONE,
            TrimMaterial.RESIN to ItemType.RESIN_CLUMP,
        )
    }

    enum class Type(val size: Int, val backSlot: Int = size - 5) {
        MAIN(9, 8),
        COLOR(54),
        PATTER(54),
        MATERIAL(54);
    }
}