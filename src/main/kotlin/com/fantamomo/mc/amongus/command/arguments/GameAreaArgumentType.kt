package com.fantamomo.mc.amongus.command.arguments

import com.fantamomo.mc.amongus.area.GameArea
import com.fantamomo.mc.amongus.area.GameAreaManager
import com.mojang.brigadier.LiteralMessage
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import java.util.concurrent.CompletableFuture

object GameAreaArgumentType : CustomArgumentType.Converted<GameArea, String> {

    private val invalidGameArea: DynamicCommandExceptionType =
        DynamicCommandExceptionType { symbol: Any -> LiteralMessage("Invalid area: $symbol") }

    override fun getNativeType(): StringArgumentType = StringArgumentType.word()

    override fun convert(nativeType: String): GameArea {
        return GameAreaManager.getArea(nativeType) ?: throw invalidGameArea.create(nativeType)
    }

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        for (area in GameAreaManager.getAreas()) {
            builder.suggest(area.name)
        }
        return builder.buildFuture()
    }
}