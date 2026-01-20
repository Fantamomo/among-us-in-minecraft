package com.fantamomo.mc.amongus.command

import com.fantamomo.mc.adventure.text.KTextComponent
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.brigadier.KtLiteralCommandBuilder
import com.fantamomo.mc.brigadier.command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

typealias PaperCommand = KtLiteralCommandBuilder<CommandSourceStack>

@OptIn(ExperimentalContracts::class)
internal inline fun paperCommand(literal: String, block: PaperCommand.() -> Unit): LiteralCommandNode<CommandSourceStack> {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return command(literal, block).build()
}

fun CommandContext<CommandSourceStack>.sendMessage(block: KTextComponent.() -> Unit) {
    source.sender.sendMessage(textComponent(block))
}