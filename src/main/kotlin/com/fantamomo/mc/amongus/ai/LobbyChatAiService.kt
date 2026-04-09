package com.fantamomo.mc.amongus.ai

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.data.AmongUsDebug
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.player.*
import com.fantamomo.mc.amongus.util.ServerThread
import kotlinx.coroutines.*
import net.kyori.adventure.text.Component
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.random.Random

@OptIn(ExperimentalAtomicApi::class)
class LobbyChatAiService(private val game: Game) {

    private val logger = LoggerFactory.getLogger("LobbyDirectorAI")
    private val scope = CoroutineScope(SupervisorJob(AmongUs.scope.coroutineContext.job))

    private var debounceJob: Job? = null

    private val lastBotMessage = ConcurrentHashMap<BotAmongUsPlayer, Long>()

    private val currentlySendingBot: AtomicReference<BotAmongUsPlayer?> = AtomicReference(null)

    fun chatMessage(sender: AmongUsPlayer) {
        if (!AiService.isEnabled()) return
        if (game.phase != GamePhase.LOBBY && game.phase != GamePhase.STARTING) return

        if (currentlySendingBot.exchange(null) != null) return

        debounceJob?.cancel()

        debounceJob = scope.launch {
            delay(1200)

            triggerAi()
        }
    }

    private suspend fun triggerAi() {
        try {
            val prompt = buildPrompt()

            logger.info("Requesting Lobby Director AI...")

            val response = AiService.run(prompt)

            handleResponse(response)

        } catch (e: CancellationException) {
            if (e.message != "StandaloneCoroutine was cancelled" && e.message != "Job was cancelled") {
                logger.error("AI request was cancelled", e)
            }
        } catch (e: Exception) {
            logger.error("AI failed", e)
        }
    }

    private fun buildPrompt(): String {
        val realPlayers = game.players
            .filterIsInstance<HumanAmongUsPlayer>()
            .joinToString("\n") { "- ${it.name} (${it.color.name})" }

        val bots = game.players
            .filterIsInstance<BotAmongUsPlayer>()
            .joinToString("\n") { "- ${it.name} (${it.color.name})" }

        val recentMessages = game.chatManager.chatHistory
            .takeLast(10)
            .joinToString("\n") { "${it.first.name}: ${it.second}" }

        val currentTimeMillis = System.currentTimeMillis()
        val botActivity = game.players
            .filterIsInstance<BotAmongUsPlayer>()
            .joinToString("\n") { bot ->
                val last = lastBotMessage[bot]
                val seconds = if (last == null) "never"
                else "${(currentTimeMillis - last) / 1000}s ago"

                "${bot.name}: last spoke $seconds"
            }

        val placeholders = mapOf(
            "real_players" to realPlayers,
            "bots" to bots,
            "recent_messages" to recentMessages,
            "bot_activity" to botActivity,
            "host" to (game.host?.name ?: "Not-Assigned"),
            "code" to game.code,
            "starting" to if (game.phase == GamePhase.STARTING)
                "The game starts in ${game.startCooldownTicks / 20} seconds."
            else
                "The game has not started yet."
        )

        return AiPrompts.LOBBY[placeholders]
    }

    private suspend fun handleResponse(response: String) {
        if (AmongUsDebug.DebugValues.LOG_AI_RESPONSE.isEnabled()) {
            logger.info("AI Response: $response")
        }

        val lines = response.lines()

        var executed = 0

        for ((index, line) in lines.withIndex()) {
            if (executed >= 2) break

            if (!line.startsWith("CHAT ")) continue

            val content = line.removePrefix("CHAT ").trim()

            val splitIndex = content.indexOf(" ")
            if (splitIndex == -1) continue

            val botName = content.substring(0, splitIndex).trim()
            val message = content.substring(splitIndex + 1).trim()

            if (message.isEmpty()) continue

            val bot = game.players
                .find { it.isBot && it.name.equals(botName, ignoreCase = true) }
                ?.bot ?: continue

            if (index < lines.lastIndex) currentlySendingBot.store(bot)
            sendMessage(bot, message)
            currentlySendingBot.store(null)

            if (index == lines.lastIndex) break

            executed++
            delay(Random.nextLong(100, 800))
        }
    }

    private suspend fun sendMessage(bot: BotAmongUsPlayer, message: String) {
        withContext(Dispatchers.ServerThread) {
            lastBotMessage[bot] = System.currentTimeMillis()
            game.chatManager.sendLobbyMessage(
                bot,
                Component.text(message)
            )
        }
    }

    fun stop() {
        debounceJob?.cancel()
        scope.cancel()
        lastBotMessage.clear()
    }
}