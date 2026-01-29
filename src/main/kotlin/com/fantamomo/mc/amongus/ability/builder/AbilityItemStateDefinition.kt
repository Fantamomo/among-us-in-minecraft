package com.fantamomo.mc.amongus.ability.builder

import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class AbilityItemStateDefinition {

    var render: AbilityContext.() -> ItemStack =
        { ItemStack(Material.BARRIER) }

    var onRightClick: AbilityContext.() -> Unit = {}

    var onLeftClick: AbilityContext.() -> Unit = {}

    var onEnter: AbilityContext.() -> Unit = {}

    var onExit: AbilityContext.() -> Unit = {}

    fun renderOverride(block: AbilityContext.() -> ItemStack) {
        render = block
    }

    fun render(block: AbilityItemRender.() -> Unit) {
        render = block::toItemStack
    }

    fun onRightClick(block: AbilityContext.() -> Unit) {
        onRightClick = block
    }

    fun onLeftClick(block: AbilityContext.() -> Unit) {
        onLeftClick = block
    }

    fun onEnter(block: AbilityContext.() -> Unit) {
        onEnter = block
    }

    fun onExit(block: AbilityContext.() -> Unit) {
        onExit = block
    }
}
