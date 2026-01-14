@file:Suppress("NOTHING_TO_INLINE")

package com.fantamomo.mc.amongus.languages

import com.fantamomo.mc.adventure.text.KTextComponent
import com.fantamomo.mc.adventure.text.KTranslatableArgsBuilder
import com.fantamomo.mc.adventure.text.arg
import com.fantamomo.mc.adventure.text.textComponent
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.minimessage.translation.Argument
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

inline fun KTranslatableArgsBuilder.bool(name: String, value: Boolean) {
    arg(Argument.bool(name, value))
}

inline fun KTranslatableArgsBuilder.numeric(name: String, value: Number) {
    arg(Argument.numeric(name, value))
}

inline fun KTranslatableArgsBuilder.string(name: String, value: String) {
    arg(Argument.string(name, value))
}

inline fun KTranslatableArgsBuilder.component(name: String, value: ComponentLike) {
    arg(Argument.component(name, value))
}

@OptIn(ExperimentalContracts::class)
inline fun KTranslatableArgsBuilder.component(name: String, block: KTextComponent.() -> Unit) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    component(name, textComponent(block))
}