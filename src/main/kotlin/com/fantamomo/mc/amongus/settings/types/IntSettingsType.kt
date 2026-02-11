package com.fantamomo.mc.amongus.settings.types

import com.fantamomo.mc.amongus.settings.SettingsType
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class IntSettingsType(
    val min: Int? = null,
    val max: Int? = null
) : SettingsType<Int> {
    override val type = Int::class
    override val argumentType: ArgumentType<Int> = IntegerArgumentType.integer(min ?: Int.MIN_VALUE, max ?: Int.MAX_VALUE)
    override fun itemRepresentation(value: Int) = ItemStack(Material.STICK)

    override fun onItemClick(current: Int): Int {
        TODO("Not yet implemented")
    }

    companion object {
        val positive = IntSettingsType(0, null)
        val negative = IntSettingsType(null, 0)

        fun min(min: Int) = IntSettingsType(min)
        fun max(max: Int) = IntSettingsType(null, max)
        fun range(min: Int, max: Int) = IntSettingsType(min, max)
    }
}