package com.fantamomo.mc.amongus.advancement.single

import com.fantamomo.mc.amongus.advancement.SingleAdvancement
import org.bukkit.inventory.ItemType

object LazyCrewmateAdvancement : SingleAdvancement("lazy_crewmate") {
    @Suppress("UnstableApiUsage")
    override val icon: ItemType = ItemType.COOKIE
    override val isSecret: Boolean = true
}