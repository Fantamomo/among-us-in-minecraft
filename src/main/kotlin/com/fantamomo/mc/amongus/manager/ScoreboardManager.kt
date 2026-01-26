package com.fantamomo.mc.amongus.manager

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.color
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.languages.component
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.task.TaskManager
import com.fantamomo.mc.amongus.util.translateTo
import com.fantamomo.mc.amongus.util.wrapComponent
import io.papermc.paper.scoreboard.numbers.NumberFormat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Bukkit
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Score
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class ScoreboardManager(private val game: Game) {

    private val scoreboards = mutableMapOf<AmongUsPlayer, AmongUsScoreboard>()

    fun start() {
        game.players.forEach { player ->
            scoreboards[player] = AmongUsScoreboard(player).also { it.show() }
        }
    }

    fun end() {
        scoreboards.values.forEach { it.hide() }
        scoreboards.clear()
    }

    fun removePlayer(player: AmongUsPlayer) {
        scoreboards.remove(player)?.hide()
    }

    fun refresh(player: AmongUsPlayer) {
        scoreboards[player]?.update()
    }

    fun refreshAll() {
        scoreboards.values.forEach { it.update() }
    }

    inner class AmongUsScoreboard(private val amongUsPlayer: AmongUsPlayer) {

        private val scoreboard = Bukkit.getScoreboardManager().newScoreboard
        private val objective = scoreboard.registerNewObjective(
            "amongus_${amongUsPlayer.uuid}",
            Criteria.DUMMY,
            textComponent { translatable("scoreboard.title") }
        ).apply {
            displaySlot = DisplaySlot.SIDEBAR
        }

        private val previousScoreboard = amongUsPlayer.player?.scoreboard
        private val usedEntries = mutableSetOf<String>()

        fun show() {
            amongUsPlayer.player?.scoreboard = scoreboard
            update()
        }

        fun hide() {
            amongUsPlayer.player?.scoreboard =
                previousScoreboard ?: Bukkit.getScoreboardManager().mainScoreboard
        }

        fun update() {
            usedEntries.clear()

            renderRole()
            renderSpacer(SPACER_ROLE)
            renderTasks()

            cleanupUnusedScores()
        }

        private fun renderRole() {
            score(ENTRY_ROLE, SCORE_ROLE_HEADER) {
                customName(
                    textComponent {
                        translatable("scoreboard.role") {
                            args {
                                component("role") {
                                    translatable(
                                        amongUsPlayer.assignedRole?.definition?.name
                                            ?: "scoreboard.role.none"
                                    )
                                }
                            }
                        }
                    }
                )
                numberFormat(NumberFormat.blank())
            }

            val description = amongUsPlayer.assignedRole
                ?.definition
                ?.description
                ?.let(Component::translatable)
                ?.translateTo(amongUsPlayer.locale)
                ?: return

            wrapComponent(description).forEachIndexed { index, line ->
                score("$ENTRY_ROLE_DESC#$index", SCORE_ROLE_DESC_START - index) {
                    customName(line)
                    numberFormat(NumberFormat.blank())
                }
            }
        }

        private fun renderTasks() {
            amongUsPlayer.tasks
                .sortedBy { it.completed }
                .forEachIndexed { index, task ->
                    val (numberFormat, color) = getScheme(task)

                    score("$ENTRY_TASK#$index", SCORE_TASK_START - index) {
                        customName(
                            textComponent {
                                translatable("tasks.${task.task.task.id}.title") {
                                    color(color)
                                }
                            }
                        )
                        numberFormat(numberFormat)
                    }
                }
        }

        private fun renderSpacer(score: Int) {
            score("$ENTRY_SPACER#$score", score) {
                customName(Component.empty())
                numberFormat(NumberFormat.blank())
            }
        }

        private fun cleanupUnusedScores() {
            scoreboard.entries
                .filterNot { it in usedEntries }
                .forEach(scoreboard::resetScores)
        }

        @OptIn(ExperimentalContracts::class)
        private inline fun score(id: String, value: Int, block: Score.() -> Unit) {
            contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

            usedEntries += id
            objective.getScore(id).apply {
                score = value
                block()
            }
        }
    }

    companion object {

        private const val ENTRY_ROLE = "role"
        private const val ENTRY_ROLE_DESC = "role_desc"
        private const val ENTRY_TASK = "task"
        private const val ENTRY_SPACER = "spacer"

        private const val SCORE_ROLE_HEADER = 1000
        private const val SCORE_ROLE_DESC_START = 900
        private const val SCORE_TASK_START = 500
        private const val SPACER_ROLE = 700

        private fun getScheme(task: TaskManager.RegisteredTask): Pair<NumberFormat, TextColor> =
            when {
                task.completed -> COMPLETED to NamedTextColor.GREEN
                task.started -> IN_PROGRESS to NamedTextColor.YELLOW
                else -> INCOMPLETE to NamedTextColor.RED
            }

        private val COMPLETED =
            NumberFormat.fixed(Component.translatable("scoreboard.task.completed"))
        private val IN_PROGRESS =
            NumberFormat.fixed(Component.translatable("scoreboard.task.in_progress"))
        private val INCOMPLETE =
            NumberFormat.fixed(Component.translatable("scoreboard.task.incomplete"))
    }
}