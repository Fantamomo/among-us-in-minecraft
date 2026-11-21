package com.fantamomo.mc.amongus.advancement.single

import com.fantamomo.mc.amongus.advancement.AdvancementListenerScope
import com.fantamomo.mc.amongus.advancement.SingleAdvancement
import org.bukkit.inventory.ItemType

object FirstBloodAdvancement : SingleAdvancement("first_blood") {
    @Suppress("UnstableApiUsage")
    override val icon: ItemType = ItemType.IRON_SWORD

    override fun AdvancementListenerScope<AssignedSingleAdvancement>.listen() {
        val kills = player.statistics.kills
        // players who already killed someone before
        if (kills.values.any { it.value > 0 }) return award()
        kills.values.forEach {
            onChange(it) { _, _ -> award() }
        }
    }
}