package com.fantamomo.mc.amongus.languages

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.translation.MiniMessageTranslator
import java.util.*

class LanguageTranslator(
    private val languages: Map<Locale, Language>,
    private val languageRoots: Map<String, Locale>,
    private val rootLocale: Locale,
    miniMessage: MiniMessage
) : MiniMessageTranslator(miniMessage) {

    override fun name(): Key = Key.key("amongus:translator")

    override fun getMiniMessageString(key: String, locale: Locale): String? {
        if (key == LanguageManager.VERSION_KEY) return null

        languages[locale]?.properties?.getProperty(key)?.let { return it }

        languageRoots[locale.language]?.let { groupRoot ->
            languages[groupRoot]?.properties?.getProperty(key)?.let { return it }
        }

        return languages[rootLocale]?.properties?.getProperty(key)
    }
}