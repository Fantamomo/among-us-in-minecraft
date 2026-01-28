package com.fantamomo.mc.amongus.command.arguments

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.command.Permissions
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.game.GameManager
import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.languages.numeric
import com.fantamomo.mc.amongus.languages.string
import com.mojang.brigadier.LiteralMessage
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.adventure.AdventureComponent
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import java.util.concurrent.CompletableFuture

class GameArgumentType(val onlyLobbyGames: Boolean = true, val showOnlyLobbyGames: Boolean = onlyLobbyGames) : CustomArgumentType<Game, String> {

    override fun parse(reader: StringReader): Game {
        val input = reader.readUnquotedString()
        if (input.length != 4) throw invalidLength.createWithContext(reader, input)
        for (char in input) if (!char.isLetterOrDigit()) throw invalidCodeChars.createWithContext(reader, input)
        val game = GameManager[input.uppercase()] ?: throw gameNotFound.createWithContext(reader, input)
        if (onlyLobbyGames && game.phase != GamePhase.LOBBY) throw gameNotFound.createWithContext(reader, input)
        return game
    }

    override fun getNativeType(): StringArgumentType = StringArgumentType.word()

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val source = context.source as? CommandSourceStack ?: return Suggestions.empty()
        if (!source.sender.hasPermission(Permissions.SEE_GAME_CODES)) return Suggestions.empty()
        val input = builder.remaining
        for (game in GameManager.getGames()) {
            if (showOnlyLobbyGames && game.phase != GamePhase.LOBBY) continue
            val code = game.code
            if (!code.startsWith(input, ignoreCase = true)) continue
            builder.suggest(code, AdventureComponent(textComponent {
                translatable("command.success.admin.game.list.game") {
                    args {
                        string("code", code)
                        string("area", game.area.name)
                        string("phase", game.phase.name.lowercase().replaceFirstChar(Char::uppercase))
                        numeric("players", game.players.size)
                        numeric("max_players", game.maxPlayers)
                    }
                }
            }))
        }
        return builder.buildFuture()
    }

    companion object {
        val INSTANCE = GameArgumentType()

        private val invalidLength = DynamicCommandExceptionType { arg ->
            LiteralMessage("Invalid code length, must be ${Game.CODE_LENGTH} characters long, got $arg")
        }

        private val invalidCodeChars = DynamicCommandExceptionType { arg ->
            LiteralMessage("Invalid code characters, must only contains A-Z and 0-9, got $arg")
        }
        private val gameNotFound = DynamicCommandExceptionType { arg ->
            LiteralMessage("Game $arg not found")
        }
    }
}