package com.fantamomo.mc.amongus.util

import com.fantamomo.mc.amongus.AmongUs
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Marker
import org.apache.logging.log4j.core.Filter.Result
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.filter.AbstractFilter
import org.apache.logging.log4j.message.Message
import kotlin.uuid.Uuid

/**
 * **Developer note:**
 *
 * This is a log filter to hide the log about unrecognized recipe loading,
 * which is caused by the way we provide the functionality to vote in the meeting.
 *
 * The recipes are removed after the meeting ended, but if a players leaves or the server stops before the recipes are removed,
 * the server will try to load those recipes on the next join and log an error for each of them.
 *
 * The log looks like this:
 * ```
 * [19:22:04 ERROR]: Tried to load unrecognized recipe: ResourceKey[minecraft:recipe / amongus:meeting/voting/skip] removed now.
 * [19:22:04 ERROR]: Tried to load unrecognized recipe: ResourceKey[minecraft:recipe / amongus:meeting/voting/f12bf674-1800-473a-bebd-efad896d7a09] removed now.
 * [19:22:04 ERROR]: Tried to load unrecognized recipe: ResourceKey[minecraft:recipe / amongus:meeting/voting/skip] removed now.
 * [19:22:04 ERROR]: Tried to load unrecognized recipe: ResourceKey[minecraft:recipe / amongus:meeting/voting/f12bf674-1800-473a-bebd-efad896d7a09] removed now.
 * ```
 * These are the logs from one player which was in a meeting with one player (the same player) and the server stopped before the meeting ended, and the player rejoined.
 *
 * The filter will hide the log if it matches the format and the uuid part is a valid uuid or "skip", otherwise it will let it pass.
 *
 * These messages are logged in [net.minecraft.stats.ServerRecipeBook.loadRecipes].
 */
object LogFilter {
    private const val START =
        "Tried to load unrecognized recipe: ResourceKey[minecraft:recipe / amongus:meeting/voting/"
    private const val END = "] removed now."
    private const val MSG_LENGTH = START.length + 36 + END.length
    private const val SKIP_MSG = START + "skip" + END

    private const val LOGGER_NAME = "net.minecraft.stats.ServerRecipeBook"

    fun init() {
        try {
            addFilter()
        } catch (e: Exception) {
            AmongUs.slF4JLogger.warn("Failed to add log filter", e)
            AmongUs.slF4JLogger.warn(
                "You might see some logs starting with '$START', you can safely ignore those."
            )
        }
    }

    private fun addFilter() {
        val root = LogManager.getRootLogger() as Logger
        root.addFilter(Filter)
    }

    private object Filter : AbstractFilter() {

        private fun filterFormatted(message: String): Result {
            if (message == SKIP_MSG) return Result.DENY
            if (message.length != MSG_LENGTH) return Result.NEUTRAL
            if (!message.startsWith(START) || !message.endsWith(END)) return Result.NEUTRAL
            val uuid = message.substring(START.length, START.length + 36)
            return Uuid.parseHexDashOrNull(uuid)?.let { Result.DENY } ?: Result.NEUTRAL
        }

        private fun isTargetLogger(logger: Logger?) =
            logger == null || logger.name == LOGGER_NAME

        private fun filterMsg(logger: Logger?, msg: String?): Result {
            if (!isTargetLogger(logger)) return Result.NEUTRAL
            if (msg == null) return Result.NEUTRAL
            return filterFormatted(msg)
        }

        /**
         * This method is called when the message we want to block is logged.
         */
        override fun filter(event: LogEvent?): Result {
            if (event == null) return Result.NEUTRAL
            return filterMsg(null, event.message.formattedMessage)
        }

        /**
         * This method is not called when the message we want to block is logged,
         * but it is for backup in case something changes.
         */
        override fun filter(
            logger: Logger?,
            level: Level?,
            marker: Marker?,
            msg: Message?,
            t: Throwable?
        ) = filterMsg(logger, msg?.formattedMessage)

        /**
         * This method is not called when the message we want to block is logged,
         * but it is for backup in case something changes.
         */
        override fun filter(
            logger: Logger?,
            level: Level?,
            marker: Marker?,
            msg: Any?,
            t: Throwable?
        ) = filterMsg(logger, msg?.toString())
    }
}