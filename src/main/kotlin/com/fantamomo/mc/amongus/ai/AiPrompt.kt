package com.fantamomo.mc.amongus.ai

class AiPrompt(
    val raw: String
) {
    operator fun get(placeholders: Map<String, String>): String {
        if (placeholders.isEmpty() || raw.indexOf('$') == -1) return raw
        return raw.replace(
            PLACEHOLDER_REGEX
        ) { matchResult ->
            placeholders[matchResult.groupValues[1]] ?: matchResult.value
        }
    }

    companion object {
        private val PLACEHOLDER_REGEX = Regex("\\$\\{(.*?)}")
    }
}