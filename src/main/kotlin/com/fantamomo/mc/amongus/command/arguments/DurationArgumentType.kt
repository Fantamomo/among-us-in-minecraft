package com.fantamomo.mc.amongus.command.arguments

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.exceptions.CommandSyntaxException
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import kotlin.time.Duration

class DurationArgumentType(private val min: Duration?, private val max: Duration?) :
    CustomArgumentType<Duration, String> {
    override fun parse(reader: StringReader): Duration {
        val text = reader.readUnquotedString()
        val duration =
            Duration.Companion.parseOrNull(text) ?: throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherParseException()
                .createWithContext(reader, "Invalid duration: $text")
        if (duration < (min ?: Duration.Companion.ZERO) || duration > (max
                ?: Duration.Companion.INFINITE)
        ) throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherParseException().createWithContext(
            reader,
            "Duration must be between ${min ?: Duration.Companion.ZERO} and ${max ?: Duration.Companion.INFINITE}: $text"
        )
        return duration
    }

    override fun getNativeType(): StringArgumentType = StringArgumentType.word()

    companion object {
        fun range(min: Duration, max: Duration) = DurationArgumentType(min, max)
        fun min(min: Duration) = DurationArgumentType(min, null)
        fun max(max: Duration) = DurationArgumentType(null, max)
        fun positive() = DurationArgumentType(Duration.ZERO, null)
        fun negative() = DurationArgumentType(null, Duration.ZERO)
    }
}