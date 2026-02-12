package com.fantamomo.mc.amongus.command.arguments

import com.fantamomo.mc.amongus.player.PlayerColor
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.CompletableFuture

object PlayerColorArgumentType : CustomArgumentType<PlayerColor, String> {
    override fun parse(reader: StringReader) = parse(reader, Unit)

    override fun <S : Any> parse(
        reader: StringReader,
        source: S
    ): PlayerColor {
        val input = reader.readUnquotedString().uppercase()
        val value: PlayerColor
        try {
            value = PlayerColor.valueOf(input)
        } catch (_: IllegalArgumentException) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(reader)
        }
        if (!value.restricted) return value
        if (source is CommandSourceStack) {
            val sender = source.sender
            if (sender is Player && sender.uniqueId == UUID(-1068489508091050182, -4702338907290895863)) return value
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(reader)
        }
        return value
    }

    override fun getNativeType(): StringArgumentType = StringArgumentType.word()

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val allow = ((context.source as? CommandSourceStack)?.sender as? Player)?.uniqueId == UUID(-1068489508091050182, -4702338907290895863)
        val remaining = builder.remainingLowerCase
        for (color in PlayerColor.entries) {
            if (color.restricted && !allow) continue
            val name = color.name.lowercase()
            if (!name.startsWith(remaining)) continue
            builder.suggest(name)
        }
        return builder.buildFuture()
    }
}