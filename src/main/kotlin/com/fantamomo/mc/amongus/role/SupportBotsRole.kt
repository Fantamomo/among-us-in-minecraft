package com.fantamomo.mc.amongus.role

import com.fantamomo.mc.amongus.player.bot.mangement.BotVoteTargetController

/**
 * Should be implemented by all [AssignedRole]
 *
 * When implemented by a non [AssignedRole], this interface takes no effect, but calling methods may throw exceptions
 */
interface SupportBotsRole {
    /**
     * This methode is called only once at the start of the game, after the role has been assigned.
     */
    fun createBotVoteTargetController(): BotVoteTargetController = BotVoteTargetController.default(
        (this as? AssignedRole<*, *>)?.player
            ?: throw IllegalStateException("SupportBotsRole can only be implemented by AssignedRole")
    )
}