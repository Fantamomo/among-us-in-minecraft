package com.fantamomo.mc.amongus.data

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.AmongUsConstants
import java.util.*
import kotlin.io.path.notExists
import kotlin.io.path.readLines

/**
 * Singleton object responsible for loading and providing debug configuration values.
 *
 * @author Fantamomo
 */
object AmongUsDebug {
    const val DEBUG_FILE_NAME = "IN_DEVELOPMENT"

    private val debugValues: Set<DebugValues> by lazy(::load)

    fun isEnabled() = AmongUsConstants.IN_DEVELOPMENT

    fun isEnabled(value: DebugValues) = isEnabled() && debugValues.contains(value)

    private fun load(): Set<DebugValues> {
        if (!isEnabled()) return emptySet()
        val path = AmongUs.dataPath.resolve(DEBUG_FILE_NAME)
        if (path.notExists()) return emptySet()
        return try {
            path.readLines()
                .mapNotNull { line -> line.trim().takeIf { it.isNotEmpty() && !it.startsWith('#') } }
                .mapNotNull { DebugValues.getOrNull(it) }
                .run { if (isEmpty()) emptySet() else EnumSet.copyOf(this) }
        } catch (_: Exception) {
            // ignore any exceptions
            emptySet()
        }
    }

    /**
     * Debug values that can be enabled in the debug file.
     */
    enum class DebugValues {
        /**
         * Displays the internal zombie used by the bots.
         */
        BOT_SHOW_ZOMBIE,

        /**
         * Displays the path that the bots will take.
         *
         * Requires the client to have the `PATHFINDING` debug propertie enabled.
         *
         * Will be ignored if [BOT_SHOW_ZOMBIE] is enabled.
         */
        BOT_SHOW_PATH,

        /**
         * Displays the goals that a bot has.
         *
         * Requires the client to have the `GOAL_SELECTOR` debug propertie enabled.
         *
         * Will be ignored if [BOT_SHOW_ZOMBIE] is enabled.
         */
        BOT_SHOW_GOALS,

        /**
         * Skips the reveal role phase.
         *
         * This is useful for testing to test features without waiting 10 seconds in the reveal role phase.
         */
        SKIP_REVEAL_ROLE_PHASE,

        /**
         * Disables the win check.
         *
         * Sets the [com.fantamomo.mc.amongus.settings.SettingsKey.DEV.DO_WIN_CHECK] to `false` on game creation.
         */
        DEFAULT_DISABLE_WIN_CHECK,

        /**
         * Don't wait for bots to vote if all players have voted.
         *
         * Used until bots can vote.
         */
        IGNORE_BOT_VOTES_ON_MEETING_END_CHECK,

        /**
         * Shows the [com.fantamomo.mc.amongus.player.bot.nav.NavGraph].
         *
         * This shows the navigation graph which is calculated to help bots navigate.
         *
         * **Warning: This is very laggy on the server and also on the client side.**
         */
        SHOW_BOT_GRAPH,

        /**
         * Logs the AI responses.
         *
         * Used in [com.fantamomo.mc.amongus.ai.LobbyChatAiService] and [com.fantamomo.mc.amongus.ai.MeetingAiService]
         */
        LOG_AI_RESPONSE;

        fun isEnabled() = isEnabled(this)

        companion object {
            fun getOrNull(value: String): DebugValues? = runCatching { valueOf(value) }.getOrNull()
        }
    }
}