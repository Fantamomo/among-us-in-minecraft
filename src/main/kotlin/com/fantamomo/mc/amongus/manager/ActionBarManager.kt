package com.fantamomo.mc.amongus.manager

import com.fantamomo.mc.adventure.text.KTextComponent
import com.fantamomo.mc.adventure.text.textComponent
import com.fantamomo.mc.amongus.game.Game
import com.fantamomo.mc.amongus.player.AmongUsPlayer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class ActionBarManager(val game: Game) {

    private val actionBars: MutableSet<ActionBar> = mutableSetOf()

    inner class ActionBar(
        val player: AmongUsPlayer,
    ) {
        var invalid = false
            private set
        private var lastTimeWasSend = false
        private val leftParts: MutableSet<ActionBarPart> = sortedSetOf()
        private val centerParts: MutableSet<ActionBarPart> = sortedSetOf()
        private val rightParts: MutableSet<ActionBarPart> = sortedSetOf()

        fun addPart(part: ActionBarPart) {
            remove(part)
            checkIsValid()
            when (part.type) {
                ActionBarPartType.LEFT -> leftParts.add(part)
                ActionBarPartType.CENTER -> centerParts.add(part)
                ActionBarPartType.RIGHT -> rightParts.add(part)
            }
        }

        fun showActionBar() {
            checkIsValid()

            val player = player.player ?: return
            // wir gehen hier davon aus das es nach priority sortiert ist (letztes element == höchste priority)
            val left = leftParts.lastOrNull { it.visible && it.component != null }?.component
            val center = centerParts.lastOrNull { it.visible && it.component != null }?.component
            val right = rightParts.lastOrNull { it.visible && it.component != null }?.component

            val elements = listOfNotNull(left, center, right)

            if (elements.isEmpty()) {
                if (lastTimeWasSend) {
                    player.sendActionBar(Component.empty())
                    lastTimeWasSend = false
                }
                return
            }
            val component = Component.join(joinConfiguration, elements)
            player.sendActionBar(component)
            lastTimeWasSend = true
        }

        fun remove(part: ActionBarPart) {
            when (part.type) {
                ActionBarPartType.LEFT -> leftParts.remove(part)
                ActionBarPartType.CENTER -> centerParts.remove(part)
                ActionBarPartType.RIGHT -> rightParts.remove(part)
            }
        }

        private fun checkIsValid() {
            if (invalid) throw IllegalStateException("This action is already valid")
        }

        fun dispose() {
            checkIsValid()
            invalid = true
            leftParts.clear()
            centerParts.clear()
            rightParts.clear()
        }
    }

    inner class ActionBarPart(
        val id: String,
        val type: ActionBarPartType,
        val priority: Int,
    ) : Comparable<ActionBarPart> {
        var visible: Boolean = true
        var component: Component? = null

        @OptIn(ExperimentalContracts::class)
        inline fun component(block: KTextComponent.() -> Unit) {
            contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
            component = textComponent(block)
        }

        override fun compareTo(other: ActionBarPart) = when {
            this.visible != other.visible -> if (this.visible) 1 else -1
            this.priority != other.priority -> if (this.priority > other.priority) 1 else -1
            else -> this.id.compareTo(other.id)
        }

        fun removeFromAllPlayers() {
            removeBarPart(this)
        }
    }

    fun tick() {
        actionBars.forEach(ActionBar::showActionBar)
    }

    fun createActionBarPart(player: AmongUsPlayer, id: String, type: ActionBarPartType, priority: Int): ActionBarPart {
        val actionBar = actionBars.find { it.player === player } ?: ActionBar(player).also(actionBars::add)
        val part = ActionBarPart(id, type, priority)
        actionBar.addPart(part)
        return part
    }

    fun removeBarPart(part: ActionBarPart) {
        for (bar in actionBars) {
            bar.remove(part)
        }
    }

    fun removeAll(player: AmongUsPlayer) {
        val actionBar = actionBars.find { it.player === player } ?: return
        actionBars.remove(actionBar)
        actionBar.dispose()
    }

    enum class ActionBarPartType {
        LEFT,
        CENTER,
        RIGHT,
    }

    companion object {
        private val joinConfiguration =
            JoinConfiguration.builder().separator { Component.text(" | ").color(NamedTextColor.DARK_GRAY) }.build()

    }
}