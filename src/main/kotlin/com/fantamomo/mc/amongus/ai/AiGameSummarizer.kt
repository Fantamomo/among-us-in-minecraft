package com.fantamomo.mc.amongus.ai

import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.manager.MeetingManager
import com.fantamomo.mc.amongus.player.info.DeadReason
import com.fantamomo.mc.amongus.player.isAlive
import com.fantamomo.mc.amongus.util.log.ActionLog
import com.fantamomo.mc.amongus.util.log.elements.GameActionElements
import com.fantamomo.mc.amongus.util.log.elements.MeetingActionElement
import com.fantamomo.mc.amongus.util.log.elements.PlayerActionElements
import com.fantamomo.mc.amongus.util.log.elements.SabotageActionElements
import com.fantamomo.mc.amongus.util.toSmartString
import com.fantamomo.mc.amongus.util.translateTo
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.util.*
import kotlin.time.DurationUnit

class AiGameSummarizer(val game: Game) {
    private val alive: MutableList<String> = mutableListOf()
    private val dead: MutableList<String> = mutableListOf()
    private val events: MutableList<String> = mutableListOf()
    private val roles: MutableList<String> = mutableListOf()
    private val timeline: MutableList<String> = mutableListOf()
    private var duration: String = ""
    private var winner: String = ""

    fun init() {
        alive.clear()
        dead.clear()
        events.clear()

        for (player in game.players) {
            if (player.isAlive()) {
                alive.add(player.name)
            } else {
                val reason = when (val deadReason = player.deadReason) {
                    null -> "unknown"
                    is DeadReason.Murdered -> "murdered by ${deadReason.murderer.name}"
                    else -> PlainTextComponentSerializer.plainText().serialize(deadReason.name.translateTo(Locale.ENGLISH))
                }
                dead.add("${player.name} ($reason)")
            }
        }

        val start = game.actionLog.entries.firstOrNull()?.timestamp
        val end = game.actionLog.entries.lastOrNull()?.timestamp

        if (start != null && end != null) {
            duration = "${end.toEpochMilliseconds() - start.toEpochMilliseconds()} ms"
        }

        game.actionLog.getFirst(GameActionElements.WinnerAnnouncement::class).let {
            winner = it?.type?.winner?.name ?: "<none>"
        }

        timeline.addAll(game.actionLog.toTimeline())
    }

    suspend fun generate(): Pair<String, String> {
        val data = mapOf(
            "alivePlayers" to alive.joinToString("\n"),
            "deadPlayers" to dead.joinToString("\n"),
            "roles" to roles.joinToString("\n"),
            "timeline" to timeline.joinToString("\n"),
            "duration" to duration,
            "winner" to winner
        )
        val prompt = AiPrompts.GAME_SUMMARIZE[data]
        val response = AiService.run(prompt)
        val text = response.split("LONG SUMMARY:")
        if (text.size != 2) throw IllegalStateException("Invalid response from AI: $response")
        val short = text[0].replace("```", "").removePrefix("SHORT SUMMARY:").trim()
        val long = text[1].replace("```", "").trim()
        return short to long
    }

    private fun ActionLog.toTimeline(): List<String> {
        val start = entries.firstOrNull()?.timestamp ?: return emptyList()
        return entries.mapNotNull { entry ->
            val time = (entry.timestamp - start).toSmartString(DurationUnit.SECONDS)

            when (val action = entry.type) {

                is PlayerActionElements.PlayerDeath -> {
                    val reason = action.reason
                    if (reason is DeadReason.Murdered) {
                        "$time: ${reason.murderer.name} killed ${game.getPlayer(action.player)!!.name}"
                    } else {
                        "$time: ${game.getPlayer(action.player)!!.name} died (${reason})"
                    }
                }

                is MeetingActionElement.Called -> {
                    val reason = when (action.reason) {
                        MeetingManager.MeetingReason.BUTTON -> "button press"
                        MeetingManager.MeetingReason.BODY -> "body found: ${action.body?.let { game.getPlayer(it)!!.name }}"
                    }
                    "$time: ${game.getPlayer(action.caller)!!.name} called a meeting ($reason)"
                }

                is MeetingActionElement.VoteFor -> {
                    "$time: ${game.getPlayer(action.voter)!!.name} voted for ${game.getPlayer(action.target)!!.name}"
                }

                is MeetingActionElement.VoteSkip -> {
                    "$time: ${game.getPlayer(action.voter)!!.name} skipped vote"
                }

                is MeetingActionElement.MeetingResult -> {
                    val name = action.ejected?.let { game.getPlayer(it)!!.name } ?: "no one"
                    "$time: Meeting result -> $name was ejected"
                }

                is SabotageActionElements.Start -> {
                    "$time: Sabotage started (${action.sabotage})"
                }

                is SabotageActionElements.End -> {
                    "$time: Sabotage ended (${action.sabotage}), fixed=${action.fixed}"
                }

                is PlayerActionElements.PlayerChat -> {
                    "$time: Chat message from ${game.getPlayer(action.player)!!.name}: ${action.message}"
                }

                else -> null
            }
        }
    }
}