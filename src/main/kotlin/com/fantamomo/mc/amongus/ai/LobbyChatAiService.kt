package com.fantamomo.mc.amongus.ai

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.data.AmongUsDebug
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.player.*
import com.fantamomo.mc.amongus.util.coroutines.ServerThread
import com.fantamomo.mc.amongus.util.coroutines.toLineFlow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectIndexed
import net.kyori.adventure.text.Component
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

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

        val lastJob = debounceJob

        if (lastJob != null && lastJob.isActive) {
            scope.launch {
                delay(100.milliseconds)
                lastJob.cancel()
            }
        }

        debounceJob = scope.launch {
            delay(1200.milliseconds)

            triggerAi()
        }
    }

    private suspend fun triggerAi() {
        try {
            val prompt = buildPrompt()

            logger.info("Requesting Lobby Director AI...")

            val flow = AiService.stream(prompt)

            var active = false

            flow.toLineFlow().collectIndexed { index, line ->
                if (index != 0) {
                    try {
                        delay(Random.nextInt(10, 800).milliseconds)
                    } catch (e: CancellationException) {
                        handleResponse(line)
                        throw e
                    }
                }
                if (line.isBlank()) return@collectIndexed
                if (handleResponse(line)) {
                    active = true
                }
            }

            if (active) {
                debounceJob = scope.launch {
                    delay(1200.milliseconds)
                    triggerAi()
                }
            }

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

    private suspend fun handleResponse(line: String): Boolean {
        if (AmongUsDebug.DebugValues.LOG_AI_RESPONSE.isEnabled()) {
            logger.info("AI Response: $line")
        }

//        val lines = response.lines()
//
//        var executed = 0
//
//        for ((index, line) in lines.withIndex()) {
//            if (executed >= 2) break

        if (!line.startsWith("CHAT ")) return false

        val content = line.removePrefix("CHAT ").trim()

        val splitIndex = content.indexOf(" ")
        if (splitIndex == -1) return false

        val botName = content.substring(0, splitIndex).trim()
        val message = content.substring(splitIndex + 1).trim()

        if (message.isEmpty()) return false

        val bot = game.players
            .find { it.isBot && it.name.equals(botName, ignoreCase = true) }
            ?.bot ?: return false

        currentlySendingBot.store(bot)
        sendMessage(bot, message)
        currentlySendingBot.store(null)

        return true
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