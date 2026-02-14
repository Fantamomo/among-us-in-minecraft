package com.fantamomo.mc.amongus.settings.types

import io.papermc.paper.datacomponent.DataComponentTypes
import org.bukkit.Material
import org.bukkit.inventory.ItemRarity
import org.bukkit.inventory.ItemStack

object PercentSettingsType : IntSettingsType(0, 100) {
    override fun itemStack(value: Int): ItemStack {
        require(value in 0..100) { "Percent value must be between 0 and 100" }

        val material = when (value) {
            0 -> Material.BARRIER
            100 -> Material.GOLD_BLOCK
            else -> getColorByPercent(value)
        }

        val itemStack = ItemStack(material)
        @Suppress("UnstableApiUsage")
        itemStack.setData(DataComponentTypes.RARITY, ItemRarity.COMMON)
        if (value in 1..99) itemStack.amount = value.coerceAtMost(64)
        return itemStack
    }

    private fun getColorByPercent(percent: Int): Material {
        return when (percent) {
            in 1..33 -> Material.ORANGE_STAINED_GLASS_PANE
            in 34..66 -> Material.YELLOW_STAINED_GLASS_PANE
            in 67..99 -> Material.LIME_STAINED_GLASS_PANE
            else -> Material.LIME_STAINED_GLASS_PANE
        }
    }

    override fun stringRepresentation(value: Int) = "$value%"
}