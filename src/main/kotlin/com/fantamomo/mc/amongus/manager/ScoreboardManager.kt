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

            val chars = splitToChars(translated)

            val existing = animatedLines[id]
            val line = if (existing == null) {
                AnimatedLine.createInitial(translated, chars, initialRender, forceAnimate = true).also {
                    animatedLines[id] = it
                }
            } else {
                existing
            }

            if (line.full != translated) {
                if (line.chars.size != chars.size) {
                    animatedLines[id] = AnimatedLine.createInitial(translated, chars, initialRender, forceAnimate = true)
                } else {
                    line.prepareCursorReplace(translated, chars)
                }
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
        var full: Component,
        var chars: List<Component>,
        var progress: Float = 0f,
        var cursorMode: Boolean = false,
        var cursorStart: Int = 0,
        var cursorEnd: Int = 0,
        var cursorPhase: Int = 0,
        var cursorProgress: Int = 0,
        var targetChars: List<Component> = emptyList()
    ) {

        companion object {
            fun createInitial(full: Component, chars: List<Component>, initialRender: Boolean, forceAnimate: Boolean = false): AnimatedLine {
                val startProgress = when {
                    forceAnimate -> 0f
                    initialRender -> 0f
                    else -> chars.size.toFloat()
                }
                return AnimatedLine(
                    full = full,
                    chars = chars,
                    progress = startProgress,
                    cursorMode = false,
                    targetChars = chars
                )
            }
        }

        fun isFinished(): Boolean {
            if (cursorMode) {
                return cursorPhase == 1 && cursorProgress >= (cursorEnd - cursorStart)
            }
            return progress >= chars.size
        }

        fun advance(speed: Float) {
            if (cursorMode) {
                advanceCursorMode(speed)
                return
            }

            if (isFinished()) return

            val remaining = chars.size - progress
            val step = (remaining * speed).coerceAtLeast(0.15f)
            progress += step
            if (progress > chars.size) progress = chars.size.toFloat()
        }

        private fun advanceCursorMode(speed: Float) {
            val totalChanged = cursorEnd - cursorStart
            if (totalChanged <= 0) {
                cursorMode = false
                progress = chars.size.toFloat()
                return
            }

            if (cursorPhase == 0) {
                val step = (1 + (totalChanged * speed).toInt()).coerceAtLeast(1)
                cursorProgress += step
                if (cursorProgress >= totalChanged) {
                    cursorPhase = 1
                    cursorProgress = 0
                }
            } else {
                val step = (1 + (totalChanged * speed).toInt()).coerceAtLeast(1)
                cursorProgress += step
                if (cursorProgress >= totalChanged) {
                    val mutable = chars.toMutableList()
                    for (i in cursorStart until cursorEnd) {
                        mutable[i] = targetChars[i]
                    }
                    chars = mutable.toList()
                    cursorMode = false
                    progress = chars.size.toFloat()
                }
            }
        }

        fun prepareCursorReplace(newFull: Component, newChars: List<Component>) {
            val size = chars.size
            var firstDiff = -1
            var lastDiff = -1
            for (i in 0 until size) {
                val a = chars[i].toString()
                val b = newChars[i].toString()
                if (a != b) {
                    if (firstDiff == -1) firstDiff = i
                    lastDiff = i
                }
            }

            if (firstDiff == -1) {
                full = newFull
                targetChars = newChars
                return
            }

            val diffLength = lastDiff - firstDiff + 1
            val threshold = (size * 0.8).toInt().coerceAtLeast(1)
            if (diffLength >= threshold) {
                full = newFull
                chars = newChars
                progress = 0f
                cursorMode = false
                targetChars = newChars
                return
            }

            full = newFull
            targetChars = newChars
            cursorMode = true
            cursorStart = firstDiff
            cursorEnd = lastDiff + 1
            cursorPhase = 0
            cursorProgress = 0
        }

        fun shown(): Component {
            if (cursorMode) {
                return buildCursorShown()
            }

            val count = progress.toInt().coerceAtMost(chars.size)
            if (chars.isEmpty()) return Component.empty()

            val revealed = chars.take(count)
            val unrevealed = chars.drop(count)

            var current = Component.empty()
            revealed.forEach { current = current.append(it) }

            unrevealed.forEach { part ->
                val style = part.style()
                current = current.append(Component.space().style(style))
            }

            return current
        }

        private fun buildCursorShown(): Component {
            val prefix = chars.subList(0, cursorStart)
            val suffix = chars.subList(cursorEnd, chars.size)

            var current = Component.empty()
            prefix.forEach { current = current.append(it) }

            val totalChanged = cursorEnd - cursorStart

            if (cursorPhase == 0) {
                for (i in 0 until totalChanged) {
                    val pos = cursorStart + i
                    val original = chars[pos]
                    val style = original.style()
                    val erased = i < cursorProgress
                    val part = if (erased) {
                        Component.space().style(style)
                    } else {
                        original
                    }
                    current = current.append(part)
                }
            } else {
                for (i in 0 until totalChanged) {
                    val pos = cursorStart + i
                    val target = targetChars[pos]
                    val style = target.style()
                    val showTyped = i < cursorProgress
                    val part = if (showTyped) {
                        target
                    } else {
                        Component.space().style(style)
                    }
                    current = current.append(part)
                }
            }

            suffix.forEach { current = current.append(it) }

            val currentCount = countCharsInComponent(current)
            val targetCount = chars.size
            if (currentCount < targetCount) {
                val padCount = targetCount - currentCount
                val padStyle = if (chars.isNotEmpty()) chars.last().style() else Component.empty().style()
                repeat(padCount) {
                    current = current.append(Component.space().style(padStyle))
                }
            }

            return current
        }

        private fun countCharsInComponent(component: Component): Int {
            var count = 0
            component.iterable(ComponentIteratorType.BREADTH_FIRST).forEach { part ->
                if (part is TextComponent) {
                    count += part.content().length
                }
            }
            return count
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

        private val COMMUNICATIONS_SABOTAGED_SCOREBOARD_LINE = textComponent { translatable("scoreboard.communications_sabotaged.info") }
        private val COMMUNICATIONS_SABOTAGED_SCOREBOARD_LINE_REPLACEMENT = textComponent { translatable("scoreboard.communications_sabotaged") }
    }
}