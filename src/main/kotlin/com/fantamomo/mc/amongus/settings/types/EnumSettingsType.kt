package com.fantamomo.mc.amongus.settings.types

import com.fantamomo.mc.amongus.command.arguments.EnumArgumentType
import com.fantamomo.mc.amongus.settings.SettingsType
import com.fantamomo.mc.amongus.util.Colored
import com.mojang.brigadier.arguments.ArgumentType
import net.kyori.adventure.text.Component
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
}