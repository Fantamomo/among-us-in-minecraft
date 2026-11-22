package com.fantamomo.mc.amongus.advancement.progressive

import com.fantamomo.mc.amongus.advancement.AdvancementListenerScope
import com.fantamomo.mc.amongus.advancement.CounterAdvancement
import com.fantamomo.mc.amongus.advancement.mirror
import org.bukkit.inventory.ItemType

/**
 * Complete 500 tasks.
 */
object TaskMasterAdvancement : CounterAdvancement("task_master", 500) {
    @Suppress("UnstableApiUsage")
    override val icon: ItemType get() = ItemType.WRITABLE_BOOK

    override fun AdvancementListenerScope<AssignedCounterAdvancement>.listen() {
        mirror(player.statistics.tasksCompleted)
    }
}
