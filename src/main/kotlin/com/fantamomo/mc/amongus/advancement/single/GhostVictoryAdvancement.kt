package com.fantamomo.mc.amongus.advancement.single

import com.fantamomo.mc.amongus.advancement.SingleAdvancement
import org.bukkit.inventory.ItemType

/**
 * Win a game while being dead.
 *
 * Example for a [SingleAdvancement] that is awarded directly from code (see [com.fantamomo.mc.amongus.game.Game]).
 */
object GhostVictoryAdvancement : SingleAdvancement("ghost_victory") {
    @Suppress("UnstableApiUsage")
    override val icon: ItemType = ItemType.PHANTOM_MEMBRANE

    override val isSecret: Boolean = true
}
