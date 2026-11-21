package com.fantamomo.mc.amongus.advancement.single

import com.fantamomo.mc.amongus.advancement.AdvancementListenerScope
import com.fantamomo.mc.amongus.advancement.SingleAdvancement
import org.bukkit.inventory.ItemType

object SelfDoubtAdvancement : SingleAdvancement("self_doubt") {
    @Suppress("UnstableApiUsage")
    override val icon: ItemType = ItemType.DAMAGED_ANVIL

    override val isSecret: Boolean = true

    override fun AdvancementListenerScope<AssignedSingleAdvancement>.listen() {
        val votedSelf = player.statistics.votedSelf
        if (votedSelf.value > 0) award()
        votedSelf.onChange { _, _ -> award() }
    }
}
