package com.fantamomo.mc.amongus.settings.types

import com.fantamomo.mc.amongus.settings.SettingsType
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.BoolArgumentType
import kotlin.reflect.KClass

object BooleanSettingsType : SettingsType<Boolean> {
    override val type: KClass<Boolean> = Boolean::class
    override val argumentType: ArgumentType<Boolean> = BoolArgumentType.bool()
}