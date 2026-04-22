package com.fantamomo.mc.amongus.command.arguments

import com.fantamomo.mc.amongus.player.PlayerManager
import com.fantamomo.mc.amongus.player.bot.BotName
import com.fantamomo.mc.amongus.player.isBot
import com.mojang.brigadier.LiteralMessage
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

class BotNameArgumentType private constructor(private val type: Type) : CustomArgumentType<BotName, String> {
    override fun parse(reader: StringReader) =
        throw UnsupportedOperationException("BotNameArgumentType does not support parsing without a source")

    override fun <S : Any> parse(
        reader: StringReader,
        source: S
    ): BotName {
        if (source !is CommandSourceStack) throw IllegalArgumentException("Source must be CommandSourceStack")
        val name = reader.readUnquotedString()
        val botName = BotName.getOrNull(name) ?: throw UNKNOWN_BOT_EXCEPTION.createWithContext(reader, name)
        val sender = source.sender as? Player
        if (sender == null || type == Type.BOTH) return botName
        val auPlayer = PlayerManager.getPlayer(sender) ?: return botName
        val players = auPlayer.game.players
        when (type) {
            Type.PLAYING_BOT -> if (players.none { it.isBot && it.botName == botName })
                throw BOT_NOT_IN_GAME.createWithContext(reader)

            Type.NON_PLAYING_BOT -> if (players.any { it.isBot && it.botName == botName })
                throw BOT_IN_GAME.createWithContext(reader)
        }
        return botName
    }

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val source = context.source as? CommandSourceStack ?: return Suggestions.empty()
        val sender = source.sender as? Player
        val amongUsPlayer = sender?.let { PlayerManager.getPlayer(it) }
        val game = amongUsPlayer?.game
        val remaining = builder.remainingLowerCase
        when (type) {
            Type.BOTH -> {
                for (name in BotName._all) {
                    if (name.name.startsWith(remaining, ignoreCase = true)) builder.suggest(name.name)
                }
            }
            Type.PLAYING_BOT -> {
                for (name in BotName._all) {
                    if (!name.name.startsWith(remaining, ignoreCase = true)) continue
                    if (game?.players?.any { it.isBot && it.botName == name } == true) {
                        builder.suggest(name.name)
                    }
                }
            }
            Type.NON_PLAYING_BOT -> {
                for (name in BotName._all) {
                    if (!name.name.startsWith(remaining, ignoreCase = true)) continue
                    if (game?.players?.none { it.isBot && it.botName == name } == true) {
                        builder.suggest(name.name)
                    }
                }
            }
        }
        return builder.buildFuture()
    }

    override fun getNativeType(): StringArgumentType = StringArgumentType.word()

    enum class Type {
        BOTH,
        PLAYING_BOT,
        NON_PLAYING_BOT;

        val argumentType: BotNameArgumentType by lazy { BotNameArgumentType(this) }
    }

    companion object {
        private val UNKNOWN_BOT_EXCEPTION = DynamicCommandExceptionType { arg ->
            LiteralMessage("Unknown bot name: $arg")
        }
        private val BOT_IN_GAME =
            SimpleCommandExceptionType(LiteralMessage("Selected bot is in a game, but this selector only allows non-playing bots"))
        private val BOT_NOT_IN_GAME =
            SimpleCommandExceptionType(LiteralMessage("Selected bot is not in a game, but this selector only allows playing bots"))

        val BOTH = Type.BOTH.argumentType
        val PLAYING_BOT = Type.PLAYING_BOT.argumentType
        val NON_PLAYING_BOT = Type.NON_PLAYING_BOT.argumentType
    }
}