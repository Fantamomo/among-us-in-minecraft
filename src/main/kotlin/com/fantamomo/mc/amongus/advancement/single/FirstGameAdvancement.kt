package com.fantamomo.mc.amongus.advancement.single

import com.fantamomo.mc.amongus.advancement.AdvancementListenerScope
import com.fantamomo.mc.amongus.advancement.SingleAdvancement
import org.bukkit.inventory.ItemType

/**
 * Finish your first game.
 *
 * Example for a listener based [SingleAdvancement] (listens to a statistic).
 */
object FirstGameAdvancement : SingleAdvancement("first_game") {
    @Suppress("UnstableApiUsage")
    override val icon: ItemType = ItemType.OAK_SAPLING

    override fun AdvancementListenerScope<AssignedSingleAdvancement>.listen() {
        val playedGames = player.statistics.playedGames
        // players who already played before this advancement existed
        if (playedGames.value > 0) return award()
        onChange(playedGames) { _, _ -> award() }
    }
}
