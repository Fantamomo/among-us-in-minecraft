package com.fantamomo.mc.amongus.advancement.single

import com.fantamomo.mc.amongus.advancement.AdvancementListenerScope
import com.fantamomo.mc.amongus.advancement.SingleAdvancement
import org.bukkit.inventory.ItemType

object InPlainSightAdvancement : SingleAdvancement("in_plain_sight") {
    @Suppress("UnstableApiUsage")
    override val icon: ItemType = ItemType.PLAYER_HEAD

    override fun AdvancementListenerScope<AssignedSingleAdvancement>.listen() {
        val killsWhileCamouflaged = player.statistics.killsWhileCamouflaged
        if (killsWhileCamouflaged.value > 0) award()
        killsWhileCamouflaged.onChange { _, _ -> award() }
    }
}