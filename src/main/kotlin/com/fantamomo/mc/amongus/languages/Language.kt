package com.fantamomo.mc.amongus.languages

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.translation.MiniMessageTranslator
import java.util.*

class Language(
    val locale: Locale,
    val properties: Properties,
    miniMessage: MiniMessage
) : MiniMessageTranslator(miniMessage) {
    override fun getMiniMessageString(key: String, locale: Locale): String? {
        if (key == LanguageManager.VERSION_KEY) return null
        return properties.getProperty(key)
    }

    override fun name(): Key = Key.key("amongus:lang_$locale")
}