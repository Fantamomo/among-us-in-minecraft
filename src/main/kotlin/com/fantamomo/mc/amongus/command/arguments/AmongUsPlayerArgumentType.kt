package com.fantamomo.mc.amongus.command.arguments

import com.fantamomo.mc.amongus.util.internal.NMS
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import io.papermc.paper.command.brigadier.argument.VanillaArgumentProviderImpl
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.selector.EntitySelector
import java.lang.reflect.Method

@NMS
class AmongUsPlayerArgumentType private constructor(private val single: Boolean, private val includeBots: Boolean) :
    CustomArgumentType<AmongUsPlayerSelectorArgumentResolver, EntitySelector> {

    @Suppress("UNCHECKED_CAST")
    private val native by lazy {
        methode.invoke(provider, if (single) EntityArgument.player() else EntityArgument.players())
                as VanillaArgumentProviderImpl.NativeWrapperArgumentType<EntitySelector, EntitySelector>
    }

    override fun parse(reader: StringReader): AmongUsPlayerSelectorArgumentResolver {
        val entitySelector = native.parse(reader)
        return AmongUsPlayerSelectorArgumentResolver(entitySelector, single, includeBots)
    }

    override fun getNativeType() = native

    companion object {
        private val provider by lazy {
            val clazz = VanillaArgumentProviderImpl::class.java.interfaces[0]
            val methode = clazz.getDeclaredMethod("provider")
            methode.isAccessible = true
            methode.invoke(null) as VanillaArgumentProviderImpl
        }
        private val methode: Method

        init {
            val clazz = VanillaArgumentProviderImpl::class.java
            methode = clazz.getDeclaredMethod("wrap", ArgumentType::class.java)
            methode.isAccessible = true
        }

        val SINGLE = AmongUsPlayerArgumentType(single = true, includeBots = true)
        val MANY = AmongUsPlayerArgumentType(single = false, includeBots = true)

        val SINGLE_NO_BOTS = AmongUsPlayerArgumentType(single = true, includeBots = false)
        val MANY_NO_BOTS = AmongUsPlayerArgumentType(single = false, includeBots = false)

        fun get(single: Boolean, includeBots: Boolean) = when {
            single && includeBots -> SINGLE
            single && !includeBots -> SINGLE_NO_BOTS
            !single && includeBots -> MANY
            else -> MANY_NO_BOTS
        }
    }
}