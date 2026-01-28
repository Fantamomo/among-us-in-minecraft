package com.fantamomo.mc.amongus.command.arguments

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.exceptions.CommandSyntaxException
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import kotlin.time.Duration

/**
 * Represents a custom argument type for parsing and validating duration values
 * within a specified range for command-line arguments.
 *
 * This argument type parses string inputs into `Duration` objects and ensures
 * that the parsed value falls within a predefined minimum and maximum range.
 *
 * @constructor Accepts an optional minimum (`min`) and maximum (`max`) duration. If not
 *   provided, `min` defaults to `Duration.ZERO` and `max` defaults to
 *   `Duration.INFINITE`.
 */
class DurationArgumentType(min: Duration?, max: Duration?) :
    CustomArgumentType<Duration, String> {

    private val min = min ?: Duration.ZERO
    private val max = max ?: Duration.INFINITE
    override fun parse(reader: StringReader): Duration {
        val text = reader.readString()
        val duration = Duration.parseOrNull(text)
            ?: throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherParseException()
                .createWithContext(reader, "Invalid duration: $text")
        if (duration !in min..max)
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherParseException()
                .createWithContext(reader, "Duration must be between $min and $max: $text")
        return duration
    }

    override fun getNativeType(): StringArgumentType = StringArgumentType.string()

    companion object {
        fun range(min: Duration, max: Duration) = DurationArgumentType(min, max)
        fun min(min: Duration) = DurationArgumentType(min, null)
        fun max(max: Duration) = DurationArgumentType(null, max)
        fun positive() = DurationArgumentType(Duration.ZERO, null)
        fun negative() = DurationArgumentType(-Duration.INFINITE, Duration.ZERO)
    }
}