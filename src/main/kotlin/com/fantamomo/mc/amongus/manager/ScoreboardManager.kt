package com.fantamomo.mc.amongus.manager

import com.fantamomo.mc.adventure.text.args
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.adventure.text.translatable
import com.fantamomo.mc.amongus.data.AmongUsConfig
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.game.GamePhase
import com.fantamomo.mc.amongus.languages.component
import com.fantamomo.mc.amongus.languages.numeric
import com.fantamomo.mc.amongus.languages.string
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import com.fantamomo.mc.amongus.sabotage.SabotageType
import com.fantamomo.mc.amongus.settings.SettingsKey
import com.fantamomo.mc.amongus.task.TaskState
import com.fantamomo.mc.amongus.util.translateTo
import com.fantamomo.mc.amongus.util.wrapComponent
import io.papermc.paper.scoreboard.numbers.NumberFormat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentIteratorType
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot

/**
 * Manages scoreboards for an Among Us game session.
 *
 * This class handles operations related to managing the scoreboards
 * of players in the game, such as updating, refreshing, and retrieving
 * scoreboards for specific players. It also manages players when they
 * join, leave, or reconnect to the game.
 *
 * @property game Holds the reference to the current game associated with the scoreboard manager.
 * @property scoreboards A mapping of players to their corresponding scoreboards.
 *
 * @author Fantamomo
 * @since 1.0-SNAPSHOT
 */
class ScoreboardManager(private val game: Game) {

    private val scoreboards = mutableMapOf<AmongUsPlayer, AmongUsScoreboard>()
    private var ticks = 0

    fun tick() {
        if (scoreboards.isEmpty()) return
        ticks++
        scoreboards.values.forEach { it.update() }
    }

    fun start() {
        if (scoreboards.isNotEmpty()) {
            scoreboards.values.forEach { it.update() }
        } else {
            game.players.forEach {
                scoreboards[it] = AmongUsScoreboard(it).also { sb -> sb.show() }
            }
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

    internal fun addLobbyPlayer(player: AmongUsPlayer) {
        if (game.phase == GamePhase.LOBBY || game.phase == GamePhase.STARTING) {
            scoreboards[player] = AmongUsScoreboard(player).also { sb -> sb.show() }
        }
    }

    inner class AmongUsScoreboard(private val player: AmongUsPlayer) {
        private val scoreboard = Bukkit.getScoreboardManager().newScoreboard

        private val animate: Boolean get() = AmongUsConfig.animateScoreboard

        /** Team used to render dead players as translucent ("ghost-like"). */
        val ghostTeam = (scoreboard.getTeam(TEAM_GHOST) ?: scoreboard.registerNewTeam(TEAM_GHOST)).apply {
            setCanSeeFriendlyInvisibles(true)
        }

        private val objective = scoreboard.registerNewObjective(
            "amongus_${player.uuid}",
            Criteria.DUMMY,
            textComponent { translatable("scoreboard.title") }
        ).apply { displaySlot = DisplaySlot.SIDEBAR }

        private val previous = player.player?.scoreboard

        private val animatedLines = mutableMapOf<String, AnimatedLine>()

        private val lastSentComponent = mutableMapOf<String, Component>()

        private val lastSentScore = mutableMapOf<String, Int>()

        private val lastSentNumberFormat = mutableMapOf<String, NumberFormat>()

        private val charSplitCache = mutableMapOf<Component, List<Component>>()

        private val usedEntries = mutableSetOf<String>()
        private val renderOrder = mutableListOf<String>()

        private var initialRender = true
        private var initialIndex = 0
        private var lineDelay = 0

        private var boardDirty = false

        fun show() {
            player.player?.scoreboard = scoreboard
            update()
        }

        fun hide() {
            player.player?.scoreboard =
                previous ?: Bukkit.getScoreboardManager().mainScoreboard
        }

        fun update() {
            val previousEntries = usedEntries.toSet()
            usedEntries.clear()
            renderOrder.clear()
            boardDirty = false

            charSplitCache.clear()

            if (game.phase == GamePhase.LOBBY || game.phase == GamePhase.STARTING) renderLobby()
            else renderGame()

            if (animate) animateInitialSequence()
            cleanup(previousEntries)
        }

        private fun renderLobby() {
            renderGameCode()
            renderSpacer(SCORE_LOBBY_SPACER_1)
            renderLobbyInfo()
            renderSpacer(SCORE_LOBBY_SPACER_2)
            renderRecentSettings()
        }

        private fun renderGame() {
            renderRoleOrModification()
            renderDeath()
            renderSpacer(SPACER_ROLE)
            renderTasks()
        }

        private fun renderGameCode() {
            val id = ENTRY_LOBBY_CODE
            register(id)
            score(
                id,
                SCORE_LOBBY_CODE,
                textComponent {
                    translatable("scoreboard.lobby.code") {
                        args { string("code", game.code) }
                    }
                }
            )
        }

        private fun renderLobbyInfo() {
            val playersId = "$ENTRY_LOBBY_INFO#players"
            register(playersId)
            score(
                playersId,
                SCORE_LOBBY_INFO_START,
                textComponent {
                    translatable("scoreboard.lobby.players") {
                        args {
                            numeric("current", game.players.size)
                            numeric("max", game.maxPlayers)
                        }
                    }
                }
            )

            val statusId = "$ENTRY_LOBBY_INFO#status"
            register(statusId)
            score(
                statusId,
                SCORE_LOBBY_INFO_START - 1,
                textComponent {
                    if (game.phase == GamePhase.LOBBY) {
                        translatable("scoreboard.lobby.status.waiting")
                    } else {
                        translatable("scoreboard.lobby.status.starting") {
                            args {
                                numeric("time", (game.startCooldownTicks - game.ticks + 19) / 20)
                            }
                        }
                    }
                }
            )
        }

        private fun renderRecentSettings() {
            val recentSettings = game.settings.getRecentlyChanged()

            recentSettings.forEachIndexed { index, key ->
                @Suppress("UNCHECKED_CAST")
                key as SettingsKey<Any, *>
                val id = "$ENTRY_LOBBY_SETTING#$index"
                register(id)

                val representation = key.type.componentRepresentation(game.settings[key])
                val nf = NumberFormat.fixed(textComponent {
                    translatable("scoreboard.lobby.settings.value") {
                        args { component("value", representation) }
                    }
                })
                score(
                    id,
                    SCORE_LOBBY_SETTINGS_START - index,
                    textComponent {
                        translatable("scoreboard.lobby.settings.name") {
                            args { component("name", key.settingsDisplayName) }
                        }
                    },
                    numberFormat = nf
                )
            }
        }

        private fun renderRoleOrModification() {
            register(ENTRY_ROLE)
            val modification = player.modification
            if (modification != null && ticks % 1000 >= 500) {
                score(
                    ENTRY_ROLE,
                    SCORE_ROLE_HEADER,
                    textComponent {
                        translatable("scoreboard.modification") {
                            args {
                                component(
                                    "modification",
                                    modification.name
                                )
                            }
                        }
                    }
                )

                val desc = modification.description

                wrapComponent(desc.translateTo(player.locale))
                    .forEachIndexed { index, line ->
                        val id = "$ENTRY_ROLE_DESC#$index"
                        register(id)
                        score(id, SCORE_ROLE_DESC_START - index, line)
                    }

                val modificationLine = modification.scoreboardLine()
                if (modificationLine != null) {
                    register(ENTRY_ROLE_CUSTOM)
                    score(ENTRY_ROLE_CUSTOM, SCORE_ROLE_CUSTOM, modificationLine)
                }
                return
            }
            score(
                ENTRY_ROLE,
                SCORE_ROLE_HEADER,
                textComponent {
                    translatable("scoreboard.role") {
                        args {
                            component(
                                "role",
                                player.assignedRole?.name
                                    ?: Component.translatable("scoreboard.role.none")
                            )
                        }
                    }
                }
            )

            val desc = player.assignedRole?.description

            if (desc != null) {
                wrapComponent(desc.translateTo(player.locale))
                    .forEachIndexed { index, line ->
                        val id = "$ENTRY_ROLE_DESC#$index"
                        register(id)
                        score(id, SCORE_ROLE_DESC_START - index, line)
                    }
            }

            val roleLine = player.assignedRole?.scoreboardLine()
            if (roleLine != null) {
                register(ENTRY_ROLE_CUSTOM)
                score(ENTRY_ROLE_CUSTOM, SCORE_ROLE_CUSTOM, roleLine)
            }
        }

        private fun renderDeath() {
            if (!player.isAlive) {
                val id = "$ENTRY_DEATH#0"
                register(id)
                score(id, SCORE_DEATH, textComponent { translatable("scoreboard.death") })
            }
        }

        private fun renderTasks() {
            val commsSabotaged = game.sabotageManager.isSabotage(SabotageType.Communications)

            if (commsSabotaged) {
                val id = "$ENTRY_TASK#sabotage"
                register(id)
                score(id, SCORE_TASK_START + 1, COMMUNICATIONS_SABOTAGED_SCOREBOARD_LINE)
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
                            COMMUNICATIONS_SABOTAGED_SCOREBOARD_LINE_REPLACEMENT,
                            numberFormat = TaskState.COMMUNICATIONS_SABOTAGED.numberFormat
                        )
                        return@forEachIndexed
                    }

                    val (defaultStyle, format) = task.state()
                    val style = if (task.completed) COMPLETED_TASK_STYLE else defaultStyle
                    val line = task.task.scoreboardLine(style)

                    score(id, SCORE_TASK_START - index, line, numberFormat = format)
                }
        }

        private fun renderSpacer(scoreValue: Int) {
            val id = "$ENTRY_SPACER#$scoreValue"
            register(id)
            score(id, scoreValue, Component.empty())
        }

        private fun register(id: String) {
            if (initialRender && id !in renderOrder) {
                renderOrder += id
            }
        }

        private fun score(
            id: String,
            value: Int,
            component: Component,
            translate: Boolean = true,
            numberFormat: NumberFormat = NumberFormat.blank()
        ) {
            val shown = score0(id, translate, component)

            val componentChanged = lastSentComponent[id] != shown
            val scoreChanged = lastSentScore[id] != value
            val numberFormatChanged = lastSentNumberFormat[id] != numberFormat

            if (!componentChanged && !scoreChanged && !numberFormatChanged) return

            boardDirty = true
            val entry = objective.getScore(id)

            if (scoreChanged) {
                entry.score = value
                lastSentScore[id] = value
            }
            if (componentChanged) {
                entry.customName(shown)
                lastSentComponent[id] = shown
            }
            if (componentChanged || numberFormatChanged) {
                entry.numberFormat(numberFormat)
                lastSentNumberFormat[id] = numberFormat
            }
        }

        private fun score0(
            id: String,
            translate: Boolean,
            component: Component
        ): Component {
            usedEntries += id

            val translated = if (translate) component.translateTo(player.locale) else component

            if (!animate) {
                animatedLines.remove(id)

                val chars = charSplitCache.getOrPut(translated) { splitToChars(translated) }
                return if (chars.size > MAX_LINE_LENGTH) buildTruncated(chars) else translated
            }

            val chars = charSplitCache.getOrPut(translated) { splitToChars(translated) }

            val existing = animatedLines[id]
            val line = existing
                ?: AnimatedLine.createInitial(translated, chars, initialRender, forceAnimate = true).also {
                    animatedLines[id] = it
                }

            if (line.full != translated) {
                if (line.chars.size != chars.size) {
                    animatedLines[id] =
                        AnimatedLine.createInitial(translated, chars, initialRender, forceAnimate = true)
                } else {
                    line.prepareCursorReplace(translated, chars)
                }
            }

            val canAnimate = !initialRender || renderOrder.getOrNull(initialIndex) == id
            if (canAnimate) {
                animatedLines[id]?.advance(ANIMATION_SPEED)
            }

            return animatedLines[id]?.shown() ?: Component.empty()
        }

        private fun buildTruncated(chars: List<Component>): Component {
            var current = Component.empty()
            chars.take(MAX_LINE_LENGTH).forEach { current = current.append(it) }
            return current
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

        private fun cleanup(previousEntries: Set<String>) {
            val removed = previousEntries - usedEntries
            if (removed.isEmpty()) return

            boardDirty = true
            removed.forEach { id ->
                scoreboard.resetScores(id)
                animatedLines.remove(id)
                lastSentComponent.remove(id)
                lastSentScore.remove(id)
                lastSentNumberFormat.remove(id)
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
        var targetChars: List<Component> = emptyList(),
        var scrollMode: Boolean = false,
        var scrollOffset: Int = 0,
        var scrollDelay: Int = 0,
        var scrollPauseCounter: Int = 0
    ) {

        companion object {
            fun createInitial(
                full: Component,
                chars: List<Component>,
                initialRender: Boolean,
                forceAnimate: Boolean = false
            ): AnimatedLine {
                val startProgress = when {
                    forceAnimate -> 0f
                    initialRender -> 0f
                    else -> chars.size.toFloat()
                }
                val needsScrolling = chars.size > MAX_LINE_LENGTH
                return AnimatedLine(
                    full = full,
                    chars = chars,
                    progress = startProgress,
                    cursorMode = false,
                    targetChars = chars,
                    scrollMode = needsScrolling,
                    scrollOffset = 0,
                    scrollDelay = 0,
                    scrollPauseCounter = if (needsScrolling) SCROLL_PAUSE_START else 0
                )
            }
        }

        fun isFinished(): Boolean {
            if (cursorMode) return cursorPhase == 1 && cursorProgress >= (cursorEnd - cursorStart)
            if (scrollMode && progress >= MAX_LINE_LENGTH.coerceAtMost(chars.size)) return true
            return progress >= chars.size
        }

        fun advance(speed: Float) {
            if (cursorMode) {
                advanceCursorMode(speed); return
            }

            if (!isFinished()) {
                val targetSize = if (scrollMode) MAX_LINE_LENGTH.coerceAtMost(chars.size) else chars.size
                val remaining = targetSize - progress
                val step = (remaining * speed).coerceAtLeast(0.15f)
                progress = (progress + step).coerceAtMost(targetSize.toFloat())
                return
            }

            if (scrollMode) advanceScrolling()
        }

        private fun advanceScrolling() {
            if (chars.size <= MAX_LINE_LENGTH) {
                scrollMode = false; return
            }
            if (scrollPauseCounter > 0) {
                scrollPauseCounter--; return
            }

            if (++scrollDelay < SCROLL_DELAY_TICKS) return
            scrollDelay = 0
            scrollOffset++

            val maxOffset = chars.size - MAX_LINE_LENGTH + ELLIPSIS.length
            if (scrollOffset > maxOffset) {
                scrollPauseCounter = SCROLL_PAUSE_END
                scrollOffset = 0
            }
        }

        private fun advanceCursorMode(speed: Float) {
            val totalChanged = cursorEnd - cursorStart
            if (totalChanged <= 0) {
                cursorMode = false
                progress = chars.size.toFloat()
                return
            }

            val step = (1 + (totalChanged * speed).toInt()).coerceAtLeast(1)

            if (cursorPhase == 0) {
                cursorProgress += step
                if (cursorProgress >= totalChanged) {
                    cursorPhase = 1; cursorProgress = 0
                }
            } else {
                cursorProgress += step
                if (cursorProgress >= totalChanged) {
                    val mutable = chars.toMutableList()
                    @Suppress("EmptyRange") // no clue why IntelliJ says that
                    for (i in cursorStart until cursorEnd) mutable[i] = targetChars[i]
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
                if (chars[i] != newChars[i]) {
                    if (firstDiff == -1) firstDiff = i
                    lastDiff = i
                }
            }

            if (firstDiff == -1) {
                full = newFull; targetChars = newChars
                scrollMode = newChars.size > MAX_LINE_LENGTH
                return
            }

            val diffLength = lastDiff - firstDiff + 1
            val threshold = (size * 0.8).toInt().coerceAtLeast(1)
            if (diffLength >= threshold) {
                full = newFull; chars = newChars; progress = 0f
                cursorMode = false; targetChars = newChars
                scrollMode = newChars.size > MAX_LINE_LENGTH
                scrollOffset = 0
                scrollPauseCounter = if (scrollMode) SCROLL_PAUSE_START else 0
                return
            }

            full = newFull; targetChars = newChars
            cursorMode = true; cursorStart = firstDiff; cursorEnd = lastDiff + 1
            cursorPhase = 0; cursorProgress = 0
        }

        fun shown(): Component = when {
            cursorMode -> buildCursorShown()
            progress < (if (scrollMode) MAX_LINE_LENGTH.coerceAtMost(chars.size) else chars.size) -> buildInitialAnimation()
            scrollMode && chars.size > MAX_LINE_LENGTH -> buildScrollingLine()
            else -> buildNormalLine()
        }

        private fun buildInitialAnimation(): Component {
            val targetSize = if (scrollMode) MAX_LINE_LENGTH.coerceAtMost(chars.size) else chars.size
            val count = progress.toInt().coerceAtMost(targetSize)
            if (chars.isEmpty()) return Component.empty()

            var current = Component.empty()
            chars.take(count).forEach { current = current.append(it) }
            chars.drop(count).take(targetSize - count).forEach { part ->
                current = current.append(Component.space().style(part.style()))
            }
            return current
        }

        private fun buildScrollingLine(): Component {
            var current = Component.empty()
            val hasStartEllipsis = scrollOffset > 0
            val ellipsisLength = if (hasStartEllipsis) ELLIPSIS.length else 0
            val visibleTextLength = MAX_LINE_LENGTH - ellipsisLength

            if (hasStartEllipsis) {
                val style = chars.firstOrNull()?.style() ?: Style.empty()
                current = current.append(Component.text(ELLIPSIS).style(style))
            }

            val startIndex = scrollOffset
            val endIndex = (startIndex + visibleTextLength).coerceAtMost(chars.size)
            for (i in startIndex until endIndex) current = current.append(chars[i])

            val paddingNeeded = MAX_LINE_LENGTH - (ellipsisLength + (endIndex - startIndex))
            if (paddingNeeded > 0) {
                val style = chars.lastOrNull()?.style() ?: Style.empty()
                repeat(paddingNeeded) { current = current.append(Component.space().style(style)) }
            }
            return current
        }

        private fun buildNormalLine(): Component {
            if (chars.isEmpty()) return Component.empty()
            var current = Component.empty()
            chars.forEach { current = current.append(it) }
            return current
        }

        private fun buildCursorShown(): Component {
            val prefix = chars.subList(0, cursorStart)
            val suffix = chars.subList(cursorEnd, chars.size)
            val totalChanged = cursorEnd - cursorStart

            var current = Component.empty()
            prefix.forEach { current = current.append(it) }

            if (cursorPhase == 0) {
                for (i in 0 until totalChanged) {
                    val original = chars[cursorStart + i]
                    val part = if (i < cursorProgress) Component.space().style(original.style()) else original
                    current = current.append(part)
                }
            } else {
                for (i in 0 until totalChanged) {
                    val target = targetChars[cursorStart + i]
                    val part = if (i < cursorProgress) target else Component.space().style(target.style())
                    current = current.append(part)
                }
            }

            suffix.forEach { current = current.append(it) }

            val padCount = chars.size - countCharsInComponent(current)
            if (padCount > 0) {
                val padStyle = chars.lastOrNull()?.style() ?: Style.empty()
                repeat(padCount) { current = current.append(Component.space().style(padStyle)) }
            }
            return current
        }

        private fun countCharsInComponent(component: Component): Int {
            var count = 0
            component.iterable(ComponentIteratorType.BREADTH_FIRST).forEach { part ->
                if (part is TextComponent) count += part.content().length
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

    internal fun onPlayerRejoin(amongUsPlayer: AmongUsPlayer) {
        scoreboards[amongUsPlayer]?.show()
    }

    fun get(target: AmongUsPlayer): AmongUsScoreboard? = scoreboards[target]

    companion object {
        private const val TEAM_GHOST: String = "ghost"

        private const val ENTRY_ROLE = "role"
        private const val ENTRY_ROLE_CUSTOM = "role_custom"
        private const val ENTRY_ROLE_DESC = "role_desc"
        private const val ENTRY_DEATH = "death"
        private const val ENTRY_TASK = "task"
        private const val ENTRY_SPACER = "spacer"
        private const val ENTRY_LOBBY_CODE = "lobby_code"
        private const val ENTRY_LOBBY_INFO = "lobby_info"
        private const val ENTRY_LOBBY_SETTING = "lobby_setting"

        private const val SCORE_ROLE_HEADER = 1000
        private const val SCORE_ROLE_DESC_START = 900
        private const val SCORE_ROLE_CUSTOM = 800
        private const val SCORE_DEATH = 700
        private const val SCORE_TASK_START = 400
        private const val SPACER_ROLE = 600

        private const val SCORE_LOBBY_CODE = 1000
        private const val SCORE_LOBBY_SPACER_1 = 900
        private const val SCORE_LOBBY_INFO_START = 800
        private const val SCORE_LOBBY_SPACER_2 = 700
        private const val SCORE_LOBBY_SETTINGS_START = 600

        private const val ANIMATION_SPEED = 0.18f
        private const val MAX_LINE_LENGTH = 32
        private const val SCROLL_DELAY_TICKS = 3
        private const val SCROLL_PAUSE_START = 40
        private const val SCROLL_PAUSE_END = 40
        private const val ELLIPSIS = "..."

        private val COMMUNICATIONS_SABOTAGED_SCOREBOARD_LINE =
            textComponent { translatable("scoreboard.communications_sabotaged.info") }
        private val COMMUNICATIONS_SABOTAGED_SCOREBOARD_LINE_REPLACEMENT =
            textComponent { translatable("scoreboard.communications_sabotaged") }

        private val COMPLETED_TASK_STYLE =
            Style.style(NamedTextColor.GREEN, TextDecoration.STRIKETHROUGH)
    }
}