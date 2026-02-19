package com.fantamomo.mc.amongus.ability.builder

import org.bukkit.inventory.ItemType
import org.bukkit.inventory.meta.ItemMeta

enum class AbilityItemState {
    ACTIVE,
    BLOCKED,
    COOLDOWN;

    open fun createDefault() = when (this) {
        COOLDOWN -> AbilityItemStateDefinition().apply {
            render = asItemStack
        }
        else -> AbilityItemStateDefinition()
    }

    companion object {
        private const val COOLDOWN_DISPLAY_NAME = "ability.general.disabled.cooldown"

        @Suppress("UnstableApiUsage")
        private val abilityItemRenderer: AbilityItemRender<ItemMeta>.() -> Unit = {
            itemType = ItemType.BARRIER
            translationKey = COOLDOWN_DISPLAY_NAME
        }
        private val asItemStack = abilityItemRenderer::toItemStack
    }
}