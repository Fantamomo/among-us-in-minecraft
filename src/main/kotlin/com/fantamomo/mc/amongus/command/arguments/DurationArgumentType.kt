package com.fantamomo.mc.amongus.command.arguments

import com.mojang.brigadier.LiteralMessage
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import net.minecraft.commands.SharedSuggestionProvider
import java.util.concurrent.CompletableFuture
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
        val duration = Duration.parseOrNull(text) ?: throw invalidDuration.createWithContext(reader, text)
        if (min == Duration.ZERO && duration < Duration.ZERO) throw negative.createWithContext(reader, duration)
        if (duration !in min..max)
            throw outOfRange.createWithContext(reader, min, max, duration)
        return duration
    }

    override fun getNativeType(): StringArgumentType = StringArgumentType.string()

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val remaining = builder.remaining
        val reader = StringReader(remaining)

        while (true) {
            try {
                reader.readFloat()
            } catch (_: CommandSyntaxException) {
                return builder.buildFuture()
            }
            if (!reader.canRead()) break
            try {
                while (reader.canRead() && reader.peek().isLetter()) reader.skip()
            } catch (_: CommandSyntaxException) {}
        }
        return SharedSuggestionProvider.suggest(UNITS, builder.createOffset(builder.start + reader.cursor))
    }

    companion object {
        private val UNITS = listOf(
            "ns", "us", "ms",
            "s", "m", "h", "d"
        )

        private val invalidDuration = DynamicCommandExceptionType { value ->
            LiteralMessage("Invalid duration: $value")
        }
        private val negative = DynamicCommandExceptionType { value ->
            LiteralMessage("Duration must be positive: $value")
        }
        private val outOfRange = Dynamic3CommandExceptionType { min, max, value ->
            LiteralMessage("Duration must be between $min and $max: $value")
        }

        fun range(min: Duration, max: Duration) = DurationArgumentType(min, max)
        fun min(min: Duration) = DurationArgumentType(min, null)
        fun max(max: Duration) = DurationArgumentType(null, max)
        fun positive() = DurationArgumentType(Duration.ZERO, null)
        fun negative() = DurationArgumentType(-Duration.INFINITE, Duration.ZERO)
    }
}