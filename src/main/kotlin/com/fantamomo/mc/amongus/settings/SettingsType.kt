package com.fantamomo.mc.amongus.settings

import com.mojang.brigadier.arguments.ArgumentType
import kotlin.reflect.KClass

interface SettingsType<T : Any> {
    val type: KClass<T>

    val argumentType: ArgumentType<T>

    fun stringRepresentation(value: T) = value.toString()
}