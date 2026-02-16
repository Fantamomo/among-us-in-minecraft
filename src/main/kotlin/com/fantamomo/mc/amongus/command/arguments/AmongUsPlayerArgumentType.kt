package com.fantamomo.mc.amongus.command.arguments

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import io.papermc.paper.command.brigadier.argument.VanillaArgumentProviderImpl
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.selector.EntitySelector
import java.lang.reflect.Method

class AmongUsPlayerArgumentType private constructor(private val single: Boolean) :
    CustomArgumentType<AmongUsPlayerSelectorArgumentResolver, EntitySelector> {

    @Suppress("UNCHECKED_CAST")
    private val native by lazy {
        methode.invoke(provider, if (single) EntityArgument.player() else EntityArgument.players())
                as VanillaArgumentProviderImpl.NativeWrapperArgumentType<EntitySelector, EntitySelector>
    }

    override fun parse(reader: StringReader): AmongUsPlayerSelectorArgumentResolver {
        val entitySelector = native.parse(reader)
        return AmongUsPlayerSelectorArgumentResolver(entitySelector, single)
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

        val SINGLE = AmongUsPlayerArgumentType(true)
        val MANY = AmongUsPlayerArgumentType(false)
    }
}