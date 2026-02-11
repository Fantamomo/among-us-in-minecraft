package com.fantamomo.mc.amongus.settings.types

import com.fantamomo.mc.amongus.settings.SettingsType
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.BoolArgumentType
import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import kotlin.reflect.KClass

@Suppress("UnstableApiUsage")
object BooleanSettingsType : SettingsType<Boolean> {
    override val type: KClass<Boolean> = Boolean::class
    override val argumentType: ArgumentType<Boolean> = BoolArgumentType.bool()

    private val TRUE_TEXT = Component.text("true", NamedTextColor.GREEN)
    private val FALSE_TEXT = Component.text("false", NamedTextColor.RED)

    override fun componentRepresentation(value: Boolean) = if (value) TRUE_TEXT else FALSE_TEXT

    private val TRUE_ITEM = ItemStack(Material.LIME_STAINED_GLASS_PANE).apply {
        setData(DataComponentTypes.ITEM_NAME, TRUE_TEXT)
    }
    private val FALSE_ITEM = ItemStack(Material.RED_STAINED_GLASS_PANE).apply {
        setData(DataComponentTypes.ITEM_NAME, FALSE_TEXT)
    }

    override fun itemRepresentation(value: Boolean) = (if (value) TRUE_ITEM else FALSE_ITEM).clone()

    override fun onItemClick(current: Boolean, action: ClickType) = !current
}