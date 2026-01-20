package com.fantamomo.mc.amongus.command.arguments

import com.fantamomo.mc.amongus.area.GameArea
import com.fantamomo.mc.amongus.area.GameAreaManager
import com.mojang.brigadier.LiteralMessage
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import io.papermc.paper.command.brigadier.argument.CustomArgumentType

object GameAreaArgumentType : CustomArgumentType.Converted<GameArea, String> {

    private val invalidGameArea: DynamicCommandExceptionType =
        DynamicCommandExceptionType { symbol: Any -> LiteralMessage("Invalid area: $symbol") }

    override fun getNativeType(): StringArgumentType = StringArgumentType.word()

    override fun convert(nativeType: String): GameArea {
        return GameAreaManager.getArea(nativeType) ?: throw invalidGameArea.create(nativeType)
    }
}