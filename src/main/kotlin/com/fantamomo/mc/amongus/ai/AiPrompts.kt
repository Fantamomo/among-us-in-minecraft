package com.fantamomo.mc.amongus.ai

import com.fantamomo.mc.amongus.role.Role
import com.fantamomo.mc.amongus.role.Team

object AiPrompts {
    private fun getOrNull(name: String): AiPrompt? {
        return javaClass.classLoader.getResourceAsStream("ai/$name.txt")?.use {
            return try {
                AiPrompt(it.bufferedReader().readText())
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun get(name: String): AiPrompt =
        getOrNull(name) ?: throw IllegalArgumentException("Prompt $name not found")

    val LOBBY = get("lobby")
    val GAME_SUMMARIZE = get("game-summarize")
    val DISCUSSION = get("discussion")
    val VOTING = get("voting")

    val roleDescriptions = Role.roles.mapNotNull { role ->
        getOrNull("roles/${role.id}")?.let { role to it }
    }.toMap()

    val teams = Team.teams.mapNotNull { team ->
        getOrNull("teams/${team.id}")?.let { team to it }
    }.toMap()
}