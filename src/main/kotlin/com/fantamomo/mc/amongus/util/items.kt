package com.fantamomo.mc.amongus.util

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.TooltipDisplay
import org.bukkit.inventory.ItemStack

@Suppress("UnstableApiUsage")
fun ItemStack.hideTooltip(): ItemStack = apply {
    setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true))
}