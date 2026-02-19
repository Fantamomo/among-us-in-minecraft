package com.fantamomo.mc.amongus.command.arguments

import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team
import com.mojang.brigadier.LiteralMessage
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.adventure.AdventureComponent
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import java.util.concurrent.CompletableFuture

class RoleArgumentType(private val roles: Collection<Role<*, *>>) : CustomArgumentType<Role<*, *>, String> {
    override fun parse(reader: StringReader): Role<*, *> {
        val input = reader.readUnquotedString()
        return roles.find { it.id.equals(input, true) } ?: throw ROLE_NOT_FOUND.createWithContext(reader, input)
    }

    override fun getNativeType() = native

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val input = builder.remainingLowerCase
        for (role in roles) {
            if (!role.id.startsWith(input, ignoreCase = true)) continue
            builder.suggest(role.id, AdventureComponent(role.name))
        }
        return builder.buildFuture()
    }

    companion object {
        private val ROLE_NOT_FOUND = DynamicCommandExceptionType { arg ->
            LiteralMessage("Role $arg not found")
        }
        private val native: StringArgumentType = StringArgumentType.word()

        val ALL = RoleArgumentType(Role.roles)
        val IMPOSTERS = RoleArgumentType(Role.roles.filter { it.team == Team.IMPOSTERS })
        val CREWMATES = RoleArgumentType(Role.roles.filter { it.team == Team.CREWMATES })
    }
}