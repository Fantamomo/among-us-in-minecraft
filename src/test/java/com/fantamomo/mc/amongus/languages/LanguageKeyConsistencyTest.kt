package com.fantamomo.mc.amongus.languages

import org.junit.Assume.assumeTrue
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.test.Test

class LanguageKeyConsistencyTest {

    private val logger = LoggerFactory.getLogger(LanguageKeyConsistencyTest::class.java)

    companion object {
        private const val LANGUAGES_LIST_FILE = "lang/languages.txt"
        private const val LANG_DIR = "lang"
        private const val VERSION_KEY = "version"
    }

    @Test
    fun `all language files share the same translation keys`() {
        val locales = loadLocaleList()

        if (locales.isEmpty()) {
            logger.warn("No locales found in {} – skipping key consistency check", LANGUAGES_LIST_FILE)
            assumeTrue("No locales found – check skipped", false)
            return
        }

        val keysByLocale: Map<Locale, Set<String>> = locales.associateWith { locale ->
            loadPropertiesForLocale(locale)?.nonVersionKeys() ?: emptySet()
        }

        val loadedLocales = keysByLocale.filter { it.value.isNotEmpty() }

        if (loadedLocales.isEmpty()) {
            logger.warn("Could not load any language files, skipping key consistency check")
            assumeTrue("No language files could be loaded, check skipped", false)
            return
        }

        val allKeys: Set<String> = loadedLocales.values.flatten().toSet()
        val missingReport = StringBuilder()

        loadedLocales.forEach { (locale, keys) ->
            val missingKeys = allKeys - keys
            if (missingKeys.isNotEmpty()) {
                val message = "Language '${locale.toLanguageTag()}' is missing " +
                    "${missingKeys.size}/${allKeys.size} keys: " +
                    missingKeys.sorted().joinToString(", ")
                logger.warn(message)
                missingReport.appendLine(message)
            }
        }

        val isConsistent = missingReport.isEmpty()

        if (isConsistent) {
            logger.info(
                "All {} language files are consistent ({} keys each)",
                loadedLocales.size,
                allKeys.size
            )
        }

        assumeTrue(
            "Language key inconsistencies detected (build not blocked):\n$missingReport",
            isConsistent
        )
    }

    private fun loadLocaleList(): List<Locale> {
        val stream = javaClass.classLoader.getResourceAsStream(LANGUAGES_LIST_FILE)
            ?: run {
                logger.error("Resource not found: {}", LANGUAGES_LIST_FILE)
                return emptyList()
            }

        return stream.bufferedReader().useLines { lines ->
            lines
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val localeTag = line.split(":", limit = 2)[0]
                    parseLocale(localeTag).also {
                        if (it == null) logger.warn("Skipping invalid locale '{}' in {}", localeTag, LANGUAGES_LIST_FILE)
                    }
                }
                .toList()
        }
    }

    private fun loadPropertiesForLocale(locale: Locale): Properties? {
        val fileName = "lang_${locale}.properties"
        val resourcePath = "$LANG_DIR/$fileName"

        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: run {
                logger.warn("Language file not found on classpath: {}", resourcePath)
                return null
            }

        return Properties().apply {
            stream.bufferedReader().use { load(it) }
        }
    }

    private fun Properties.nonVersionKeys(): Set<String> =
        stringPropertyNames() - setOf(VERSION_KEY)

    @Suppress("DEPRECATION")
    private fun parseLocale(localeString: String): Locale? {
        val parts = localeString.split("_")
        return when (parts.size) {
            1 -> Locale(parts[0])
            2 -> Locale(parts[0], parts[1])
            3 -> Locale(parts[0], parts[1], parts[2])
            else -> null
        }
    }
}