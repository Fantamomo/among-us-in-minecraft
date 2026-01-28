package com.fantamomo.mc.amongus.command.arguments

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import java.lang.ref.WeakReference
import java.util.concurrent.CompletableFuture
import kotlin.enums.enumEntries
import kotlin.reflect.KClass

/**
 * Enum argument type with configurable parsing, suggestions and caching.
 *
 * - Default instances are cached (ignoreCase = true, lowercase suggestions)
 * - Custom configurations are NOT cached
 */
class EnumArgumentType<E : Enum<E>> @PublishedApi internal constructor(
    private val enumClass: KClass<E>,
    private val values: List<E>,
    private val ignoreCase: Boolean,
    private val suggestionMapper: (E) -> String,
    private val parser: (String, List<E>) -> E?
) : CustomArgumentType<E, String> {

    override fun parse(reader: StringReader): E {
        val input = reader.readUnquotedString()
        return parser(input, values)
            ?: throw IllegalArgumentException(
                "Invalid value '$input' for enum ${enumClass.simpleName}. " +
                        "Allowed values: ${values.joinToString { it.name }}"
            )
    }

    override fun getNativeType(): ArgumentType<String> = NATIVE_TYPE

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        values
            .map(suggestionMapper)
            .filter { it.startsWith(builder.remaining, ignoreCase) }
            .forEach(builder::suggest)
        return builder.buildFuture()
    }

    companion object {
        private val NATIVE_TYPE = StringArgumentType.word()
        private val DEFAULT_PARSER: (String, List<Enum<*>>) -> Enum<*>? =
            { input, values -> values.firstOrNull { it.name.equals(input, ignoreCase = true) } }

        @Suppress("UNCHECKED_CAST")
        fun <E : Enum<E>> defaultParser(): (String, List<E>) -> E? = DEFAULT_PARSER as (String, List<E>) -> E?

        /**
         * Cache ONLY for default instances.
         */
        @PublishedApi
        internal val defaultCache:
                MutableMap<KClass<out Enum<*>>, WeakReference<EnumArgumentType<*>>> = mutableMapOf()

        /**
         * Default enum argument:
         * - ignoreCase = true
         * - suggestions = lowercase enum names
         * - cached
         */
        inline fun <reified E : Enum<E>> of(): EnumArgumentType<E> {
            val enumClass = E::class
            val cached = defaultCache[enumClass]?.get()
            @Suppress("UNCHECKED_CAST")
            if (cached != null) return cached as EnumArgumentType<E>

            val values = enumEntries<E>()

            @Suppress("UNCHECKED_CAST")
            val instance = EnumArgumentType(
                enumClass = enumClass,
                values = values,
                ignoreCase = true,
                suggestionMapper = { it.name.lowercase() },
                parser = defaultParser()
            )

            defaultCache[enumClass] = WeakReference(instance)
            return instance
        }

        inline fun <reified E : Enum<E>> builder(): Builder<E> =
            Builder(E::class, enumEntries<E>())

        fun <E : Enum<E>> builder(enumClass: KClass<E>): Builder<E> =
            Builder(enumClass, enumClass.java.enumConstants.toList())
    }

    enum class SuggestionFormat {
        ORIGINAL,
        LOWERCASE,
        UPPERCASE
    }

    class Builder<E : Enum<E>> @PublishedApi internal constructor(
        private val enumClass: KClass<E>,
        enumValues: List<E>
    ) {
        private val values: List<E> = enumValues.toList()

        private var ignoreCase: Boolean = true
        private var filter: (E) -> Boolean = { true }

        private var suggestionMapper: (E) -> String = { it.name.lowercase() }

        private var parser: (String, List<E>) -> E? = defaultParser()

        fun ignoreCase(value: Boolean) = apply {
            ignoreCase = value
        }

        fun filter(predicate: (E) -> Boolean) = apply {
            filter = predicate
        }

        fun suggestionFormat(format: SuggestionFormat) = apply {
            suggestionMapper = when (format) {
                SuggestionFormat.ORIGINAL -> { e: E -> e.name }
                SuggestionFormat.LOWERCASE -> { e: E -> e.name.lowercase() }
                SuggestionFormat.UPPERCASE -> { e: E -> e.name.uppercase() }
            }
        }

        fun suggestionMapper(mapper: (E) -> String) = apply {
            suggestionMapper = mapper
        }

        fun parser(customParser: (String, List<E>) -> E?) = apply {
            parser = customParser
        }

        fun build(): EnumArgumentType<E> {
            val filteredValues = values.filter(filter)

            return EnumArgumentType(
                enumClass = enumClass,
                values = filteredValues,
                ignoreCase = ignoreCase,
                suggestionMapper = suggestionMapper,
                parser = parser
            )
        }
    }
}