package com.fantamomo.mc.amongus.role

import com.fantamomo.mc.amongus.ai.AiPrompt
import com.fantamomo.mc.amongus.ai.AiPrompts
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.BotAmongUsPlayer

interface RoleDescriptionPromptProvider {
    fun getPromptPlaceholders(bot: BotAmongUsPlayer): Map<String, String>

    fun getPromptText(bot: BotAmongUsPlayer): String {
        require(this === bot.role.definition.team || this === bot.role) { "parameter bot must be of this team or this role" }
        val prompt = getPrompt(bot) ?: return ""

        val team = bot.role.definition.team
        if (team === this) return prompt[getPromptPlaceholders(bot)]
        val placeholders = (team as? RoleDescriptionPromptProvider)?.getPromptPlaceholders(bot)?.toMutableMap() ?: mutableMapOf()
        placeholders.putAll(getPromptPlaceholders(bot))
        return prompt[placeholders]
    }

    companion object {
        private fun getPrompt(bot: AmongUsPlayer): AiPrompt? {
            val role = bot.role.definition
            return AiPrompts.roleDescriptions[role] ?: AiPrompts.teams[role.team]
        }
    }
}