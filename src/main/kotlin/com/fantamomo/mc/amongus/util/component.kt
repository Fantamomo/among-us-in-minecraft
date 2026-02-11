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

fun Component.translateTo(locale: Locale): Component = GlobalTranslator.render(this, locale)

fun Component.translateFor(audience: Audience): Component {
    val locale = (audience as? Player)?.locale()
        ?: audience.getOrDefault(Identity.LOCALE, null)
        ?: return this
    return translateTo(locale)
}

inline fun textComponent(translateFor: Audience, block: KTextComponent.() -> Unit) = textComponent(block).translateFor(translateFor)

inline fun textComponent(translateTo: Locale, block: KTextComponent.() -> Unit) = textComponent(block).translateTo(translateTo)

@OptIn(ExperimentalContracts::class)
inline fun Audience.sendComponent(block: KTextComponent.() -> Unit) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    sendMessage(textComponent(block))
}

// Edit Components

data class ComponentWrapConfig(
    val maxLineLength: Int = 30,
    val wordWrap: Boolean = true,
    val maxLines: Int? = null
)

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