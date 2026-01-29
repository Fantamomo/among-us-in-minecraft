package com.fantamomo.mc.amongus.manager

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.languages.component
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.sabotage.SabotageType
import com.fantamomo.mc.amongus.task.TaskState
import com.fantamomo.mc.amongus.util.translateTo
import com.fantamomo.mc.amongus.util.wrapComponent
import io.papermc.paper.scoreboard.numbers.NumberFormat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentIteratorType
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Score
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class ScoreboardManager(private val game: Game) {

    private val scoreboards = mutableMapOf<AmongUsPlayer, AmongUsScoreboard>()

    fun tick() {
        if (scoreboards.isEmpty()) return
        scoreboards.values.forEach { it.update() }
    }

    fun start() {
        game.players.forEach {
            scoreboards[it] = AmongUsScoreboard(it).also { sb -> sb.show() }
        }
    }

    fun end() {
        scoreboards.values.forEach { it.hide() }
        scoreboards.clear()
    }

    fun refresh(player: AmongUsPlayer) {
        scoreboards[player]?.update()
    }

    internal fun removePlayer(player: AmongUsPlayer) {
        scoreboards.remove(player)?.hide()
    }

    // =======================================================

    inner class AmongUsScoreboard(private val player: AmongUsPlayer) {

        private val scoreboard = Bukkit.getScoreboardManager().newScoreboard

        private val objective = scoreboard.registerNewObjective(
            "amongus_${player.uuid}",
            Criteria.DUMMY,
            textComponent { translatable("scoreboard.title") }
        ).apply { displaySlot = DisplaySlot.SIDEBAR }

        private val previous = player.player?.scoreboard

        private val usedEntries = mutableSetOf<String>()
        private val animatedLines = mutableMapOf<String, AnimatedLine>()
        private val renderOrder = mutableListOf<String>()

        private var initialRender = true
        private var initialIndex = 0
        private var lineDelay = 0

        fun show() {
            player.player?.scoreboard = scoreboard
            update()
        }

        fun hide() {
            player.player?.scoreboard =
                previous ?: Bukkit.getScoreboardManager().mainScoreboard
        }

        fun update() {
            usedEntries.clear()
            renderOrder.clear()

            renderRole()
            renderDeath()
            renderSpacer(SPACER_ROLE)
            renderTasks()

            animateInitialSequence()
            cleanup()
        }

        private fun renderRole() {

            register(ENTRY_ROLE)

            score(
                ENTRY_ROLE,
                SCORE_ROLE_HEADER,
                textComponent {
                    translatable("scoreboard.role") {
                        args {
                            component("role") {
                                translatable(
                                    player.assignedRole?.definition?.name
                                        ?: "scoreboard.role.none"
                                )
                            }
                        }
                    }
                }
            )

            val desc = player.assignedRole
                ?.definition
                ?.description
                ?.let(Component::translatable)
                ?: return

            wrapComponent(desc.translateTo(player.locale))
                .forEachIndexed { index, line ->

                    val id = "$ENTRY_ROLE_DESC#$index"

                    register(id)

                    score(
                        id,
                        SCORE_ROLE_DESC_START - index,
                        line
                    )
                }
        }

        private fun renderDeath() {
            if (!player.isAlive) {

                val id = "$ENTRY_DEATH#0"

                register(id)

                score(
                    id,
                    SCORE_DEATH,
                    textComponent { translatable("scoreboard.death") }
                )
            }
        }

        private fun renderTasks() {
            val commsSabotaged = game.sabotageManager.isSabotage(SabotageType.Communications)

            if (commsSabotaged) {
                val id = "$ENTRY_TASK#sabotage"
                register(id)
                score(
                    id,
                    SCORE_TASK_START + 1,
                    COMMUNICATIONS_SABOTAGED_SCOREBOARD_LINE
                )
            }

            player.tasks
                .sortedBy { it.completed }
                .forEachIndexed { index, task ->

                    val id = "$ENTRY_TASK#$index"

                    register(id)

                    if (commsSabotaged) {
                        score(
                            id,
                            SCORE_TASK_START - index,
                            COMMUNICATIONS_SABOTAGED_SCOREBOARD_LINE_REPLACEMENT
                        ) {
                            numberFormat(TaskState.COMMUNICATIONS_SABOTAGED.numberFormat)
                        }
                        return@forEachIndexed
                    }

                    val (color, format) = task.state()

                    val line = if (task.completed) {
                        task.task.scoreboardLine()
                            .color(NamedTextColor.GREEN)
                            .decorate(TextDecoration.STRIKETHROUGH)
                    } else {
                        task.task.scoreboardLine().color(color)
                    }

                    score(
                        id,
                        SCORE_TASK_START - index,
                        line
                    ) {
                        numberFormat(format)
                    }
                }
        }

        private fun renderSpacer(score: Int) {

            val id = "$ENTRY_SPACER#$score"

            register(id)

            score(
                id,
                score,
                Component.empty()
            )
        }

        private fun register(id: String) {
            if (initialRender && id !in renderOrder) {
                renderOrder += id
            }
        }

        @OptIn(ExperimentalContracts::class)
        private inline fun score(
            id: String,
            value: Int,
            component: Component,
            block: Score.() -> Unit = {}
        ) {
            contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

            usedEntries += id

            val translated = component.translateTo(player.locale)

            val line = animatedLines.getOrPut(id) {
                val chars = splitToChars(translated)
                AnimatedLine(
                    full = translated,
                    chars = chars,
                    progress = if (initialRender) 0f else chars.size.toFloat()
                )
            }

            if (line.full != translated) {
                animatedLines[id] = AnimatedLine(
                    full = translated,
                    chars = splitToChars(translated),
                    progress = 0f
                )
            }

            val canAnimate =
                !initialRender || renderOrder.getOrNull(initialIndex) == id

            if (canAnimate) {
                animatedLines[id]?.advance(ANIMATION_SPEED)
            }

            val shown = animatedLines[id]?.shown() ?: Component.empty()

            objective.getScore(id).apply {
                score = value
                customName(shown)
                numberFormat(NumberFormat.blank())
                block()
            }
        }

        private fun animateInitialSequence() {
            if (!initialRender) return

            val currentId = renderOrder.getOrNull(initialIndex) ?: run {
                initialRender = false
                return
            }

            val line = animatedLines[currentId] ?: return

            if (line.isFinished()) {
                lineDelay++

                if (lineDelay >= 3) {
                    lineDelay = 0
                    initialIndex++
                }
            }
        }

        private fun cleanup() {
            scoreboard.entries
                .filterNot { it in usedEntries }
                .forEach {
                    scoreboard.resetScores(it)
                    animatedLines.remove(it)
                }
        }
    }

    private data class AnimatedLine(
        val full: Component,
        val chars: List<Component>,
        var progress: Float = 0f
    ) {

        fun isFinished() = progress >= chars.size

        fun advance(speed: Float) {
            if (isFinished()) return

            val remaining = chars.size - progress
            val step = (remaining * speed).coerceAtLeast(0.15f)

            progress += step
            if (progress > chars.size) progress = chars.size.toFloat()
        }

        fun shown(): Component {
            val count = progress.toInt().coerceAtMost(chars.size)
            if (count == 0) return Component.empty()
            if (count == chars.size) return full
            return chars.take(count).joinToComponent()
        }
    }

    private fun splitToChars(component: Component): List<Component> {
        val result = mutableListOf<Component>()

        component.iterable(ComponentIteratorType.BREADTH_FIRST).forEach { part ->
            if (part !is TextComponent) return@forEach

            val style = part.style()

            part.content().forEach { c ->
                result += Component.text(c.toString()).style(style)
            }
        }

        return result
    }

    companion object {

        private const val ENTRY_ROLE = "role"
        private const val ENTRY_ROLE_DESC = "role_desc"
        private const val ENTRY_DEATH = "death"
        private const val ENTRY_TASK = "task"
        private const val ENTRY_SPACER = "spacer"

        private const val SCORE_ROLE_HEADER = 1000
        private const val SCORE_ROLE_DESC_START = 900
        private const val SCORE_DEATH = 800
        private const val SCORE_TASK_START = 500
        private const val SPACER_ROLE = 700

        private const val ANIMATION_SPEED = 0.18f

        private fun List<Component>.joinToComponent(): Component {
            var current = Component.empty()
            forEach { current = current.append(it) }
            return current
        }

        private val COMMUNICATIONS_SABOTAGED_SCOREBOARD_LINE = textComponent { translatable("scoreboard.communications_sabotaged.info") }
        private val COMMUNICATIONS_SABOTAGED_SCOREBOARD_LINE_REPLACEMENT = textComponent { translatable("scoreboard.communications_sabotaged") }
    }
}