package com.fantamomo.mc.amongus.advancement.single

import com.fantamomo.mc.amongus.advancement.AdvancementListenerScope
import com.fantamomo.mc.amongus.advancement.SingleAdvancement
import org.bukkit.inventory.ItemType

object WinnerWinnerAdvancement : SingleAdvancement("winner_winner") {
    @Suppress("UnstableApiUsage")
    override val icon: ItemType = ItemType.GOLDEN_APPLE

    override fun AdvancementListenerScope<AssignedSingleAdvancement>.listen() {
        val winsWith = player.statistics.winsWith
        // players who already played before this advancement existed
        if (winsWith.values.any { it.value > 0 }) return award()
        winsWith.values.forEach {
            onChange(it) { _, _ -> award() }
        }
    }
}