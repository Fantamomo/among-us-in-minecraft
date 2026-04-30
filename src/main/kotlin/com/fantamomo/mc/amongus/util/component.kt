package com.fantamomo.mc.amongus.util

import com.fantamomo.mc.adventure.text.KTextComponent
import com.fantamomo.mc.adventure.text.textComponent
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.identity.Identity
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentIteratorType
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.translation.GlobalTranslator
import org.bukkit.entity.Player
import java.util.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Translates the component to the given locale.
 *
 * @return a new component with the translated text.
 */
fun Component.translateTo(locale: Locale): Component = GlobalTranslator.render(this, locale)

/**
 * Translates the component to the locale of the given audience.
 *
 * If the local cannot be determined, the component is returned unmodified.
 *
 * @return a new component with the translated text.
 */
fun Component.translateFor(audience: Audience): Component {
    val locale = (audience as? Player)?.locale()
        ?: audience.getOrDefault(Identity.LOCALE, null)
        ?: return this
    return translateTo(locale)
}

/**
 * Creates a new component with [block] and then translates it to the locale of the given audience.
 *
 * @see com.fantamomo.mc.adventure.text.textComponent
 * @see translateFor
 */
inline fun textComponent(translateFor: Audience, block: KTextComponent.() -> Unit) =
    textComponent(block).translateFor(translateFor)

/**
 * Creates a new component with [block] and then translates it to the given locale.
 *
 * @see com.fantamomo.mc.adventure.text.textComponent
 * @see translateTo
 */
inline fun textComponent(translateTo: Locale, block: KTextComponent.() -> Unit) =
    textComponent(block).translateTo(translateTo)

/**
 * Sends a component to the given audience.
 *
 * @see com.fantamomo.mc.adventure.text.textComponent
 * @see sendMessage
 */
@OptIn(ExperimentalContracts::class)
inline fun Audience.sendComponent(block: KTextComponent.() -> Unit) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    sendMessage(textComponent(block))
}

// Edit Components

/**
 * Configuration class for component wrapping behavior.
 *
 * @property maxLineLength Specifies the maximum line length for content wrapping. Default is 30.
 * @property wordWrap Indicates whether word wrapping is enabled. Default is true.
 * @property maxLines Specifies the maximum number of lines to display. Null indicates no limit.
 *
 * @see wrapComponent
 */
data class ComponentWrapConfig(
    val maxLineLength: Int = 30,
    val wordWrap: Boolean = true,
    val maxLines: Int? = null
)

/**
 * Splits a given component into a list of smaller components according to the specified wrapping configuration.
 *
 * This method processes the content of the provided component, breaking it into smaller components
 * based on the word wrapping rules and line length constraints defined in the configuration.
 * It ensures that components do not exceed the maximum line length and can handle scenarios
 * where a maximum number of lines is specified.
 *
 * This is mainly used by the [com.fantamomo.mc.amongus.manager.ScoreboardManager]
 * to split translated components that have been loaded from a file.
 *
 * Providing a [net.kyori.adventure.text.TranslatableComponent] to this function will not work,
 * as it does not have a content string.
 *
 * @param component The component to be wrapped. It is traversed in a breadth-first manner to split its content.
 * @param config The configuration for wrapping the component.
 */
fun wrapComponent(
    component: Component,
    config: ComponentWrapConfig = ComponentWrapConfig()
): List<Component> {

    val result = mutableListOf<Component>()
    var current = Component.empty()
    var currentLength = 0

    fun flush() {
        if (currentLength == 0) return
        result += current
        current = Component.empty()
        currentLength = 0
    }

    fun append(text: String, style: Style) {
        current = current.append(Component.text(text).style(style))
        currentLength += text.length
    }

    component.iterable(ComponentIteratorType.BREADTH_FIRST).forEach { part ->
        if (part !is TextComponent) return@forEach

        val style = part.style()
        val text = part.content()

        val tokens = Regex("\\S+|\\s+").findAll(text).map { it.value }

        for (token in tokens) {

            if (token.contains('\n')) {
                val lines = token.split('\n')
                lines.forEachIndexed { index, line ->
                    if (line.isNotEmpty()) {
                        append(line, style)
                    }
                    if (index < lines.lastIndex) {
                        flush()
                    }
                }
                continue
            }

            val isWhitespace = token.all { it.isWhitespace() }

            if (isWhitespace) {
                if (currentLength + token.length <= config.maxLineLength) {
                    append(token, style)
                }
                continue
            }

            if (currentLength + token.length > config.maxLineLength) {
                flush()
            }

            append(token, style)

            if (config.maxLines != null && result.size >= config.maxLines) {
                return result
            }
        }
    }

    flush()

    return result
}

/**
 * Splits the provided component into a list of components, preserving style information and line breaks.
 *
 * This method processes the given component, recursively handling its children, and ensures that each line
 * of text in the component is split into a separate component while preserving the styles and formatting.
 * Components without direct text content will pass their children through the process.
 *
 * This is mainly used by the [com.fantamomo.mc.amongus.settings.SettingsInventory] to display infos in the tooltip,
 * that have been loaded from a file.
 *
 * [component] is **EXPECTED** to be a [TextComponent] with content.
 * Any other component will be **IGNORED** (the children will be processed).
 *
 * @param component The root component to be split into individual lines, preserving styles.
 * @return A list of components, where each component represents a single line of text with its respective styles.
 */
fun splitLinesPreserveStyles(component: Component): List<Component> {
    val lines = mutableListOf<Component>()
    var currentLine = Component.empty()

    fun pushLine() {
        lines += currentLine
        currentLine = Component.empty()
    }

    fun appendComponentKeepingNewlines(c: Component) {
        when (c) {
            is TextComponent -> {
                val style = c.style()
                val parts = c.content().split('\n')

                parts.forEachIndexed { i, part ->
                    if (part.isNotEmpty()) {
                        currentLine = currentLine.append(Component.text(part).style(style))
                    }
                    if (i != parts.lastIndex) {
                        pushLine()
                    }
                }

                c.children().forEach(::appendComponentKeepingNewlines)
            }

            else -> {
                c.children().forEach(::appendComponentKeepingNewlines)
            }
        }
    }

    appendComponentKeepingNewlines(component)
    pushLine()

    return lines
}