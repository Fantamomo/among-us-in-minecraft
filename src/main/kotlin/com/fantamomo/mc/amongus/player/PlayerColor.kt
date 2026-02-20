package com.fantamomo.mc.amongus.player

import com.fantamomo.mc.amongus.util.hideTooltip
import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Color
import org.bukkit.inventory.ItemType
import org.bukkit.inventory.meta.ArmorMeta
import org.bukkit.inventory.meta.ColorableArmorMeta
import org.bukkit.inventory.meta.trim.ArmorTrim
import kotlin.random.Random

@Suppress("UnstableApiUsage")
enum class PlayerColor(val color: Color, val helmet: ItemType.Typed<out ArmorMeta>, val restricted: Boolean = false) {
    WHITE(Color.WHITE),
    SILVER(Color.SILVER),
    GRAY(Color.GRAY),
    BLACK(Color.BLACK),
    RED(Color.RED),
    MARRON(Color.MAROON),
    YELLOW(Color.YELLOW),
    OLIVE(Color.OLIVE),
    LIME(Color.LIME),
    GREEN(Color.GREEN),
    AQUA(Color.AQUA),
    TEAL(Color.TEAL),
    NAVY(Color.NAVY),
    FUCHSIA(Color.FUCHSIA),
    PURPLE(Color.PURPLE),
    ORANGE(Color.ORANGE),

    TURTLE(Color.GREEN, ItemType.TURTLE_HELMET, true),
    CHAINMAIL(Color.SILVER, ItemType.CHAINMAIL_HELMET, true),
    COPPER(Color.ORANGE, ItemType.COPPER_HELMET, true),
    IRON(Color.WHITE, ItemType.IRON_HELMET, true),
    GOLD(Color.YELLOW, ItemType.GOLDEN_HELMET, true),
    DIAMOND(Color.AQUA, ItemType.DIAMOND_HELMET, true),
    @Suppress("SpellCheckingInspection")
    NETHERITE(Color.BLACK, ItemType.NETHERITE_HELMET, true)

    ;

    val textColor = TextColor.color(color.asRGB())
    val coloredName = Component.text(name.lowercase(), textColor)
    val capitalizeColoredName = Component.text(name.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }, textColor)

    internal val cooldownGroup = Key.key("amongus", "player_color/cooldown_group/${name.lowercase()}")

    constructor(color: Color) : this(color, ItemType.LEATHER_HELMET, false)

    fun toItemStack(trim: ArmorTrim? = null) = helmet.createItemStack { config ->
        if (config is ColorableArmorMeta) {
            config.setColor(color)
        }
        if (trim != null) config.trim = trim
    }.apply {
        unsetData(DataComponentTypes.ATTRIBUTE_MODIFIERS)
        setData(DataComponentTypes.UNBREAKABLE)
        hideTooltip()
    }

    fun rawItemStack() = helmet.createItemStack { config ->
        if (config is ColorableArmorMeta) {
            config.setColor(color)
        }
    }

    companion object {
        private val notRestrictedColors = entries.filter { !it.restricted }
        private val restrictedColors = entries.filter { it.restricted }
        val helmetTypes = entries.map { it.helmet }

        fun random(allowRestricted: Boolean = false, random: Random = Random): PlayerColor {
            if (allowRestricted) return entries.random(random)
            return notRestrictedColors.random(random)
        }

        fun notRestrictedColors() = notRestrictedColors.toMutableList()
    }
}