package com.fantamomo.mc.amongus.command.arguments

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import io.papermc.paper.registry.TypedKey
import org.bukkit.Keyed
import java.util.concurrent.CompletableFuture

class RegistryArgumentType<T : Keyed>(private val registryKey: RegistryKey<T>) : CustomArgumentType<T, TypedKey<T>> {
    val type: ArgumentType<TypedKey<T>> = ArgumentTypes.resourceKey(registryKey)
    val registry = RegistryAccess.registryAccess().getRegistry(registryKey)
    override fun parse(reader: StringReader): T {
        val key = type.parse(reader)
        return registry.get(key) ?: throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
            .createWithContext(reader)
    }

    override fun getNativeType() = type

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> = nativeType.listSuggestions(context, builder)
}