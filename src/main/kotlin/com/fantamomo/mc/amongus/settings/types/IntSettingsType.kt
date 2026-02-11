package com.fantamomo.mc.amongus.settings.types

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.settings.SettingsType
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ItemLore
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack

class IntSettingsType(
    val min: Int? = null,
    val max: Int? = null
) : SettingsType<Int> {
    override val type = Int::class
    override val argumentType: ArgumentType<Int> = IntegerArgumentType.integer(min ?: Int.MIN_VALUE, max ?: Int.MAX_VALUE)

    @Suppress("UnstableApiUsage")
    override fun itemRepresentation(value: Int) = ItemStack(Material.COAL).apply {
        if (min == null && max == null) return@apply
        val lore = ItemLore.lore()
        if (min != null) lore.addLine(textComponent {
            translatable("setting.ui.type.int.min") {
                args {
                    string("min", min.toString())
                }
            }
        })
        if (max != null) lore.addLine(textComponent {
            translatable("setting.ui.type.int.max") {
                args {
                    string("max", max.toString())
                }
            }
        })
        setData(DataComponentTypes.LORE, lore)
    }

    override fun componentRepresentation(value: Int) = super.componentRepresentation(value).color(NamedTextColor.BLUE)

    override fun onItemClick(current: Int, action: ClickType): Int {
        val candidate = when (action) {
            ClickType.LEFT -> current + 1
            ClickType.RIGHT -> current - 1
            ClickType.SHIFT_LEFT -> current + 10
            ClickType.SHIFT_RIGHT -> current - 10
            else -> current + 1
        }

        val clampedMin = min?.let { kotlin.math.max(candidate, it) } ?: candidate
        val clamped = max?.let { kotlin.math.min(clampedMin, it) } ?: clampedMin
        return clamped
    }

    companion object {
        val positive = IntSettingsType(0, null)
        val negative = IntSettingsType(null, 0)

        fun min(min: Int) = IntSettingsType(min)
        fun max(max: Int) = IntSettingsType(null, max)
        fun range(min: Int, max: Int) = IntSettingsType(min, max)
    }
}