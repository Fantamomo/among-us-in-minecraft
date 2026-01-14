package com.fantamomo.mc.amongus.util

import com.fantamomo.mc.adventure.text.KTextComponent
import com.fantamomo.mc.adventure.text.textComponent
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.identity.Identity
import net.kyori.adventure.text.Component
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