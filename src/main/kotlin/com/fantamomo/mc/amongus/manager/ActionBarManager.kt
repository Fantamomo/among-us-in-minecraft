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

class ActionBarManager(private val game: Game) {

    private val bars = mutableMapOf<AmongUsPlayer, ActionBar>()
    private val globalParts = mutableSetOf<ActionBarPart>()

    fun tick() {
        bars.values.forEach { it.tick() }
    }

    fun bar(player: AmongUsPlayer): ActionBar =
        bars.getOrPut(player) { ActionBar(player) }

    fun removeAll(player: AmongUsPlayer) {
        bars.remove(player)?.dispose()
    }

    fun addGlobalPart(part: ActionBarPart) {
        globalParts += part
        bars.values.forEach { it.add(part) }
    }

    fun removeGlobalPart(part: ActionBarPart) {
        globalParts -= part
        bars.values.forEach { it.remove(part) }
    }

    inner class ActionBar(private val owner: AmongUsPlayer) {

        private val slots = ActionBarPartType.entries.associateWith {
            sortedSetOf<ActionBarPart>()
        }

        fun add(part: ActionBarPart) {
            slots[part.type]!!.remove(part)
            slots[part.type]!!.add(part)
            part.onAdd?.invoke(owner)
        }

        fun remove(part: ActionBarPart) {
            if (slots[part.type]!!.remove(part)) {
                part.onRemove?.invoke(owner)
            }
        }

        fun tick() {
            val player = owner.player ?: return

            val context = RenderContext(owner)

            val components = ActionBarPartType.entries.mapNotNull { type ->
                slots[type]
                    ?.onEach { it.tick(context) }
                    ?.lastOrNull { it.visible }
                    ?.componentLike
                    ?.invoke()
            }

            if (components.isEmpty()) {
                player.sendActionBar(Component.empty())
                return
            }

            player.sendActionBar(
                Component.join(JOIN_CONFIG, components)
            )
        }

        fun dispose() {
            slots.values.flatten().forEach {
                it.onRemove?.invoke(owner)
            }
            slots.values.forEach { it.clear() }
        }

        fun removeIfEmpty() {
            if (slots.values.flatten().isNotEmpty()) return
            dispose()
            bars.remove(owner)
        }
    }

    inner class ActionBarPart(
        val id: String,
        val type: ActionBarPartType,
        val priority: Int,
        val isGlobal: Boolean = false,
        val expireAfterTicks: Int? = null,
    ) : Comparable<ActionBarPart> {

        var visible: Boolean = true
        var componentLike: (() -> Component?)? = null

        private var age = 0

        var onAdd: ((AmongUsPlayer) -> Unit)? = null
        var onRemove: ((AmongUsPlayer) -> Unit)? = null
        var onTick: ((RenderContext) -> Unit)? = null

        @OptIn(ExperimentalContracts::class)
        fun component(block: KTextComponent.() -> Unit) {
            contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
            componentLike = textComponent(block)::asComponent
        }

        internal fun tick(context: RenderContext) {
            onTick?.invoke(context)

            expireAfterTicks?.let {
                age++
                if (age >= it) {
                    visible = false
                }
            }
        }

        override fun compareTo(other: ActionBarPart): Int =
            compareValuesBy(
                this,
                other,
                { it.visible },
                { it.priority },
                { it.id }
            )

        fun remove() {
            for (entry in bars) {
                entry.value.remove(this)
                entry.value.removeIfEmpty()
            }
        }
    }

    class RenderContext(val player: AmongUsPlayer)

    @OptIn(ExperimentalContracts::class)
    fun part(
        player: AmongUsPlayer,
        id: String,
        type: ActionBarPartType,
        priority: Int,
        componentLike: () -> Component?,
        expireAfterTicks: Int? = null
    ): ActionBarPart {
        val part = ActionBarPart(id, type, priority, false, expireAfterTicks)
        part.componentLike = componentLike
        bar(player).add(part)
        return part
    }

    fun part(
        player: AmongUsPlayer,
        id: String,
        type: ActionBarPartType,
        priority: Int,
        expireAfterTicks: Int? = null
    ): ActionBarPart {
        val part = ActionBarPart(id, type, priority, false, expireAfterTicks)
        bar(player).add(part)
        return part
    }

    companion object {
        private val JOIN_CONFIG = JoinConfiguration.builder()
            .separator(Component.text(" | ").color(NamedTextColor.DARK_GRAY))
            .build()
    }

    enum class ActionBarPartType {
        LEFT,
        CENTER,
        RIGHT
    }
}
