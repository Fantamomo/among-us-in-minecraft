package com.fantamomo.mc.amongus.command.arguments

import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.game.GameManager
import com.mojang.brigadier.LiteralMessage
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import io.papermc.paper.command.brigadier.argument.CustomArgumentType

object GameArgumentType : CustomArgumentType.Converted<Game, String> {

    private val invalidLength = DynamicCommandExceptionType { arg ->
        LiteralMessage("Invalid code length, must be 4 characters long, got $arg")
    }

    private val invalidCodeChars = DynamicCommandExceptionType { arg ->
        LiteralMessage("Invalid code characters, must only contains A-Z and 0-9, got $arg")
    }
    private val gameNotFound = DynamicCommandExceptionType { arg ->
        LiteralMessage("Game $arg not found")
    }

    override fun convert(nativeType: String): Game {
        if (nativeType.length != 4) throw invalidLength.create(nativeType)
        for (char in nativeType) if (!char.isLetterOrDigit()) throw invalidCodeChars.create(nativeType)
        return GameManager[nativeType.uppercase()] ?: throw gameNotFound.create(nativeType)
    }

    override fun getNativeType() = StringArgumentType.word()
}