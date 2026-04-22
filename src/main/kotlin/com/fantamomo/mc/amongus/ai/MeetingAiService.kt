package com.fantamomo.mc.amongus.ai

import com.fantamomo.mc.amongus.AmongUs
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.manager.MeetingManager
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.player.BotAmongUsPlayer
import com.fantamomo.mc.amongus.player.isAlive
import com.fantamomo.mc.amongus.player.isHuman
import com.fantamomo.mc.amongus.role.RoleDescriptionPromptProvider
import com.fantamomo.mc.amongus.role.crewmates.MayorRole
import com.fantamomo.mc.amongus.util.coroutines.toLineFlow
import com.fantamomo.mc.amongus.util.toSmartString
import io.ktor.util.collections.*
import kotlinx.coroutines.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.slf4j.LoggerFactory
import kotlin.math.exp
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.Instant

class MeetingAiService(val game: Game) {
    private val logger = LoggerFactory.getLogger("MeetingAiService")
    private val botAnswerChance: MutableMap<BotAmongUsPlayer, AnswerChance> = ConcurrentMap()

    private val aiJobs: MutableList<AiJob> = mutableListOf()

    private class AiJob(
        val bot: BotAmongUsPlayer,
        val receivedCommands: MutableList<String>,
        val job: Job,
        val meeting: MeetingManager.Meeting,
        var state: AiJobState = AiJobState.RUNNING
    ) {
        var waitingJob: Job? = null
    }

    private enum class AiJobState(val canBeRemoved: Boolean) {
        RUNNING(false),
        FINISHED(true),
        WAITING(false),
        CANCELLED(true),
        FAILED(true)
    }

    private data class AnswerChance(val chance: Double, val lastAction: Instant)

    fun onChatMessage(sender: AmongUsPlayer) {
        if (!AiService.isEnabled()) return
        if (game.phase != GamePhase.DISCUSSION && game.phase != GamePhase.VOTING) return

        val meeting = game.meetingManager.meeting ?: return

        for (job in aiJobs) {
            @Suppress("SENSELESS_COMPARISON")
            if (job == null) continue
            when (job.state) {
                AiJobState.RUNNING -> {
                    job.waitingJob = AmongUs.scope.launch {
                        job.state = AiJobState.WAITING
                        delay(500.milliseconds)
                        job.job.cancel()
                    }
                }

                AiJobState.WAITING -> {
                    job.waitingJob?.cancel()
                    job.waitingJob = null
                    job.state = AiJobState.CANCELLED
                    job.job.cancel()
                }

                else -> {}
            }
        }

        @Suppress("UNCHECKED_CAST")
        (aiJobs as MutableList<AiJob?>).removeAll { it == null || it.state.canBeRemoved }

        for (player in game.players) {
            if (sender === player) continue
            if (player.isHuman) continue
            val chance = botAnswerChance.getOrDefault(player, AnswerChance(Random.nextDouble(), Instant.DISTANT_PAST))
            if (chance.chance > Random.nextDouble()) {
                requestAi(player, meeting)
            }
        }

        recalculateChances(sender)
    }

    // this methode was originally written by AI and then modified by Fantamomo
    private fun recalculateChances(sender: AmongUsPlayer) {
        val now = Clock.System.now()
        val messages = game.chatManager.chatHistory.takeLast(30)

        val senderName = sender.name.lowercase()

        for (player in game.players) {
            if (player.isHuman) continue

            val previous = botAnswerChance[player] ?: AnswerChance(
                chance = Random.nextDouble() * 0.3,
                lastAction = Instant.DISTANT_PAST
            )

            var score = BASE_SCORE

            messages.forEachIndexed { index, (author, text) ->
                val ageFactor = index.toDouble() / messages.size
                val decay = exp(-3 * (1 - ageFactor))

                val lower = text.lowercase()

                if (lower.contains(player.name.lowercase())) {
                    score += 0.25 * decay
                }

                if (lower.contains(senderName)) {
                    score += 0.12 * decay
                }

                if (author.name.equals(sender.name, ignoreCase = true)
                    && lower.contains(player.name.lowercase())
                ) {
                    score += 0.35 * decay
                }
            }

            val window = messages.takeLast(10)

            val botMentions = window.count { it.second.contains(player.name, ignoreCase = true) }
            val senderMentions = window.count { it.second.contains(sender.name, ignoreCase = true) }

            score += botMentions * 0.08
            score += senderMentions * 0.05

            if (botMentions >= 3) {
                score += 0.25
            }

            val botMessages = window.count {
                it.first.name.equals(player.name, ignoreCase = true)
            }

            val senderMessages = window.count {
                it.first.name.equals(sender.name, ignoreCase = true)
            }

            if (botMessages == 0) score += 0.2
            if (senderMessages >= 3) score += 0.15

            val secondsSinceLastAction =
                (now - previous.lastAction).inWholeSeconds.coerceAtLeast(1)

            val minutes = secondsSinceLastAction / 60.0

            score += (1 - exp(-0.15 * minutes)) * 0.4

            if (secondsSinceLastAction < 20) {
                score -= 0.3
            }

            val recentActivity = window.size / 10.0
            score += recentActivity * 0.15

            val finalChance = score.coerceIn(0.0, 1.0)

            botAnswerChance[player] = AnswerChance(
                chance = finalChance,
                lastAction = previous.lastAction
            )
        }
    }

    private fun requestAi(bot: BotAmongUsPlayer, meeting: MeetingManager.Meeting) {
        val commands: MutableList<String> = mutableListOf()
        var aiJob: AiJob? = null
        val job = AmongUs.scope.launch(start = CoroutineStart.LAZY) {
            try {
                val phase = game.phase
                if (phase == GamePhase.DISCUSSION) {
                    callDiscussionAi(bot, meeting, commands)
                } else if (phase == GamePhase.VOTING) {
                    callVotingAi(bot, meeting, commands)
                }
            } catch (e: CancellationException) {
                aiJob?.state = AiJobState.CANCELLED
            } catch (e: Exception) {
                logger.error("Meeting: Bot ${bot.name} AI error", e)
                aiJob?.state = AiJobState.FAILED
            } finally {
                if (aiJob?.state == AiJobState.RUNNING) aiJob?.state = AiJobState.FINISHED
            }
        }

        aiJob = AiJob(bot, commands, job, meeting)
        aiJobs.add(aiJob)
        job.start()
    }

    private suspend fun callVotingAi(
        bot: BotAmongUsPlayer,
        meeting: MeetingManager.Meeting,
        commands: MutableList<String>
    ) {
        val aiPrompt = AiPrompts.VOTING
        val data = defaultPlaceholders(bot, meeting)

        val hasVoted = meeting.hasVoted(bot)
        val voteTarget = meeting.getVoteTarget(bot)
        val voteMessage = when {
            !hasVoted -> "You have not voted yet."
            voteTarget != null -> "You have voted for ${voteTarget.name}"
            else -> "You have voted to skip."
        }
        data["voted"] = voteMessage

        var prompt = aiPrompt[data]

        if (bot.role.definition === MayorRole) {
            prompt = prompt.replace("\n- You can vote ONCE per meeting (VOTE or SKIP).", "")
        }

        val lines = AiService.stream(prompt).toLineFlow()
        lines.collect { line ->
            commands.add(line)
            executeCommand(bot, line, meeting)
        }
    }

    private suspend fun callDiscussionAi(
        bot: BotAmongUsPlayer,
        meeting: MeetingManager.Meeting,
        commands: MutableList<String>
    ) {
        val aiPrompt = AiPrompts.DISCUSSION
        val data = defaultPlaceholders(bot, meeting)
        val prompt = aiPrompt[data]
        val lines = AiService.stream(prompt).toLineFlow()
        lines.collect { line ->
            commands.add(line)
            executeCommand(bot, line, meeting)
        }
    }

    private fun defaultPlaceholders(bot: BotAmongUsPlayer, meeting: MeetingManager.Meeting): MutableMap<String, String> = mutableMapOf(
        "name" to bot.name,
        "color" to bot.color.name.lowercase(),
        "role" to bot.role.definition.id,
        "team" to bot.role.definition.team.name.lowercase(),
        "role_description" to PlainTextComponentSerializer.plainText().serialize(bot.role.definition.description),
        "meeting_number" to meeting.number.toString(),
        "time_left" to (meeting.timeLeft()?.toSmartString(DurationUnit.SECONDS) ?: "Unknown"),
        "phase" to game.phase.name.lowercase(),
        "alive_players" to game.players.filter { it.isAlive() }.joinToString("\n") { it.name },
        "dead_players" to game.players.filter { !it.isAlive() }.joinToString("\n") { it.name },
        "chat_log" to game.chatManager.chatHistory.takeLast(15).joinToString("\n") { "${it.first.name}: ${it.second}" },
        "memory_list" to bot.memory.getAll().joinToString("\n") { "${it.id}: ${it.value}" },
        "role_description" to getRoleDescription(bot)
    )

    private fun getRoleDescription(bot: BotAmongUsPlayer): String {
        val role = bot.role
        if (role is RoleDescriptionPromptProvider) return role.getPromptText(bot)
        val team = role.definition.team
        if (team is RoleDescriptionPromptProvider) return team.getPromptText(bot)
        return AiPrompts.roleDescriptions[role.definition]?.raw ?: AiPrompts.teams[team]?.raw ?: ""
    }

    private suspend fun executeCommand(bot: BotAmongUsPlayer, command: String, meeting: MeetingManager.Meeting) {
        logger.info("Meeting: Bot ${bot.name}: $command")
        val commands = command.split(" ")
        val commandType = commands.firstOrNull() ?: return
        val argument = commands.drop(1)
        when (commandType.uppercase()) {
            "NO" -> return
            "CHAT" -> {
                if (argument.isNotEmpty()) game.chatManager.sendMeetingMessage(
                    bot,
                    Component.text(argument.joinToString(" "))
                )
            }

            "MEMORY" -> {
                val subCommand = argument.firstOrNull() ?: return
                val value = argument.drop(1).joinToString(" ")
                when (subCommand.uppercase()) {
                    "ADD" -> if (value.isNotBlank()) bot.memory.add(value)
                    "REMOVE" -> value.toIntOrNull()?.let { bot.memory.remove(it) }
                }
            }

            "VOTE" -> {
                if (meeting.hasVoted(bot) && (bot.role.definition !== MayorRole || meeting.hasVoted(bot, true))) return
                val target = argument.joinToString(" ")
                val targetPlayer = game.players.find { it.name.equals(target, ignoreCase = true) } ?: return
                meeting.voteFor(bot, targetPlayer)
            }

            "SKIP" -> {
                if (meeting.hasVoted(bot) && (bot.role.definition !== MayorRole || meeting.hasVoted(bot, true))) return
                meeting.voteSkip(bot)
            }
        }
        botAnswerChance.computeIfPresent(bot) { _, old ->
            old.copy(lastAction = Clock.System.now())
        }
    }

    fun clear() {
        botAnswerChance.clear()
        aiJobs.forEach {
            it.job.cancel()
            it.waitingJob?.cancel()
        }
        aiJobs.clear()
    }

    companion object {
        private const val BASE_SCORE = 0.15
    }
}