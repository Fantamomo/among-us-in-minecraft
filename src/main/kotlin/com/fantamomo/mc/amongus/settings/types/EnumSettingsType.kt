package com.fantamomo.mc.amongus.settings.types

import com.fantamomo.mc.amongus.command.arguments.EnumArgumentType
import com.fantamomo.mc.amongus.settings.SettingsType
import com.fantamomo.mc.amongus.util.Colored
import com.fantamomo.mc.amongus.util.MaterialProvider
import com.mojang.brigadier.arguments.ArgumentType
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import kotlin.reflect.KClass

class EnumSettingsType<E : Enum<E>> private constructor(override val type: KClass<E>) : SettingsType<E> {
    override val argumentType: ArgumentType<E> by lazy { EnumArgumentType.builder(type).build() }

    companion object {
        fun <E : Enum<E>> of(enumClass: KClass<E>) = EnumSettingsType(enumClass)
        inline fun <reified E : Enum<E>> create() = of(E::class)
    }

    override fun componentRepresentation(value: E) =
        if (value is Colored) Component.text(value.name, value.color())
        else Component.text(value.name)

    override fun itemRepresentation(value: E) = ItemStack((value as? MaterialProvider)?.material() ?: Material.STONE)

    override fun onItemClick(current: E, action: ClickType): E {
        val values = (argumentType as EnumArgumentType<E>).values
        if (values.size == 1) return values.first()
        val index = values.indexOf(current)
        val target = when (action) {
            ClickType.LEFT -> index + 1
            ClickType.SHIFT_LEFT -> values.size-1
            ClickType.RIGHT -> index - 1
            ClickType.SHIFT_RIGHT -> 0
            else -> index + 1
        }
        var i = target % values.size
        if (i < 0) i += values.size
        return values[i]
    }
}