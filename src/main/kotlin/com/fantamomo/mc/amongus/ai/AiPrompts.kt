package com.fantamomo.mc.amongus.ai

object AiPrompts {
    private fun get(name: String): AiPrompt {
        javaClass.classLoader.getResourceAsStream("ai/$name.txt").use {
            if (it != null) {
                return AiPrompt(it.bufferedReader().readText())
            }
        }
        throw IllegalArgumentException("Prompt '$name' not found in resources.")
    }

    val LOBBY = get("lobby")
    val GAME_SUMMARIZE = get("game-summarize")
}