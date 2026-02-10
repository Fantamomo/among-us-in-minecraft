package com.fantamomo.mc.amongus.settings.types

import com.fantamomo.mc.amongus.settings.SettingsType
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.BoolArgumentType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import kotlin.reflect.KClass

object BooleanSettingsType : SettingsType<Boolean> {
    override val type: KClass<Boolean> = Boolean::class
    override val argumentType: ArgumentType<Boolean> = BoolArgumentType.bool()

    private val TRUE = Component.text("true", NamedTextColor.GREEN)
    private val FALSE = Component.text("false", NamedTextColor.RED)

    override fun componentRepresentation(value: Boolean) = if (value) TRUE else FALSE
}