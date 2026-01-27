package com.fantamomo.mc.amongus.command.arguments

import com.fantamomo.mc.amongus.task.Task
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.adventure.AdventureComponent
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import net.kyori.adventure.text.Component
import java.util.concurrent.CompletableFuture

object TaskIdArgumentType : CustomArgumentType<Task<*, *>, String> {
    override fun parse(reader: StringReader): Task<*, *> {
        val id = reader.readUnquotedString()
        return Task.tasks.find { it.id == id } ?: throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherParseException().createWithContext(reader, "Invalid task id: $id")
    }

    override fun getNativeType(): StringArgumentType = StringArgumentType.word()

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val input = builder.remaining
        for (task in Task.tasks) {
            val id = task.id
            if (!id.startsWith(input, ignoreCase = true)) continue
            builder.suggest(
                id,
                AdventureComponent(Component.translatable(id))
            )
        }

        return builder.buildFuture()
    }
}