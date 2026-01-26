package com.fantamomo.mc.amongus.manager

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.color
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.languages.component
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.task.TaskManager
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

    inner class AmongUsScoreboard(private val player: AmongUsPlayer) {

        private val scoreboard = Bukkit.getScoreboardManager().newScoreboard
        private val objective = scoreboard.registerNewObjective(
            "amongus_${player.uuid}",
            Criteria.DUMMY,
            textComponent { translatable("scoreboard.title") }
        )

        private val previous = player.player?.scoreboard
        private val usedEntries = mutableSetOf<String>()

        init {
            objective.displaySlot = DisplaySlot.SIDEBAR
        }

        fun show() {
            player.player?.scoreboard = scoreboard
            update()
        }

        fun hide() {
            player.player?.scoreboard = previous ?: Bukkit.getScoreboardManager().mainScoreboard
        }

        fun update() {
            usedEntries.clear()

            renderRole()
            renderSpacer(0)
            renderTasks()

            cleanupUnusedScores()
        }

        private fun renderRole() {
            score("role", 1000) {
                customName(textComponent {
                    translatable("scoreboard.role") {
                        args {
                            component("role") {
                                translatable(
                                    this@AmongUsScoreboard.player.assignedRole?.definition?.name
                                        ?: "scoreboard.role.none"
                                )
                            }
                        }
                    }
                })
                numberFormat(NumberFormat.blank())
            }
        }

        private fun renderSpacer(index: Int) {
            score("empty#$index", 900 - index) {
                customName(Component.empty())
                numberFormat(NumberFormat.blank())
            }
        }

        private fun renderTasks() {
            val tasks = player.tasks.sortedBy { it.completed }
            val baseScore = 500

            tasks.forEachIndexed { index, task ->
                score("task#$index", baseScore - index) {
                    val format = getScheme(task)
                    customName(textComponent {
                        translatable("tasks.${task.task.task.id}.title") {
                            color(format.second)
                        }
                    })
                    numberFormat(format.first)
                }
            }
        }

        private fun cleanupUnusedScores() {
            scoreboard.entries
                .filter { entry ->
                    scoreboard.getObjective(DisplaySlot.SIDEBAR)
                        ?.getScore(entry)
                        ?.objective == objective &&
                            entry !in usedEntries
                }
                .forEach { entry ->
                    scoreboard.resetScores(entry)
                }
        }


        @OptIn(ExperimentalContracts::class)
        private inline fun score(id: String, value: Int, block: Score.() -> Unit) {
            contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

            usedEntries += id
            val score = objective.getScore(id)
            score.score = value
            score.block()
        }
    }

    companion object {
        private fun getScheme(task: TaskManager.RegisteredTask): Pair<NumberFormat, TextColor> = when {
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
