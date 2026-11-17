package com.fantamomo.mc.amongus.advancement.progressive

import com.fantamomo.mc.amongus.advancement.AdvancementListenerScope
import com.fantamomo.mc.amongus.advancement.CounterAdvancement
import com.fantamomo.mc.amongus.advancement.mirror
import org.bukkit.inventory.ItemType

/**
 * Play 100 games.
 */
object VeteranAdvancement : CounterAdvancement("veteran", 100) {
    @Suppress("UnstableApiUsage")
    override val icon: ItemType = ItemType.CLOCK

    override fun AdvancementListenerScope<AssignedCounterAdvancement>.listen() {
        mirror(player.statistics.playedGames)
    }
}
