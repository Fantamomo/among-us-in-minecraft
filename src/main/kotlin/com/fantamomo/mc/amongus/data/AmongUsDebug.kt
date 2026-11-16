package com.fantamomo.mc.amongus.data

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.AmongUsConstants
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.io.path.notExists
import kotlin.io.path.readLines

/**
 * Singleton object responsible for loading and providing debug configuration values.
 *
 * @author Fantamomo
 */
object AmongUsDebug {
    private val logger = LoggerFactory.getLogger(AmongUsDebug::class.java)

    const val DEBUG_FILE_NAME = "IN_DEVELOPMENT"

    private val debugValues: Set<DebugValues> by lazy(::load)

    init {
        if (DebugValues.PRINT_DEBUG_VALUES.isEnabled()) {
            logger.info("Debug values: {}", debugValues.joinToString(", "))
        }
    }

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
         * Prints the debug values to the console.
         */
        PRINT_DEBUG_VALUES,

        /**
         * Displays the internal zombie used by the bots.
         */
        BOT_SHOW_ZOMBIE,

        /**
         * Displays the path that the bots will take.
         *
         * Requires the client to have the `PATHFINDING` debug property enabled.
         *
         * Will be ignored if [BOT_SHOW_ZOMBIE] is enabled.
         */
        BOT_SHOW_PATH,

        /**
         * Displays the goals that a bot has.
         *
         * Requires the client to have the `GOAL_SELECTOR` debug property enabled.
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
        LOG_AI_RESPONSE,

        /**
         * Stops the plugin from deleting previously created game worlds on startup
         */
        DISABLE_DELETING_GAME_WORLDS_ON_STARTUP,

        /**
         * Sets some default values in settings for debugging.
         *
         * This includes things like cooldowns which are set to very low values, to avoid waiting for cooldowns.
         *
         * This **not** includes the [com.fantamomo.mc.amongus.settings.SettingsKey.DEV.DO_WIN_CHECK],
         * because this is already done via [DEFAULT_DISABLE_WIN_CHECK]
         */
        SET_SETTING_VALUES_FOR_DEBUG;

        fun isEnabled() = isEnabled(this)

        companion object {
            fun getOrNull(value: String): DebugValues? = runCatching { valueOf(value) }.getOrNull()
        }
    }
}